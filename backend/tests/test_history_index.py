import asyncio
import unittest
from unittest import mock

from couchbase.exceptions import CouchbaseException, QueryIndexAlreadyExistsException

from api import main


def run_lifespan_with_history_index_error(error):
    cluster = mock.MagicMock()

    def query(statement, *args, **kwargs):
        if "idx_scan_history" in statement and error is not None:
            raise error
        return mock.MagicMock(rows=lambda: [])

    cluster.query.side_effect = query

    async def enter_and_exit():
        async with main.lifespan(main.app):
            pass

    with mock.patch.object(main, "Cluster", return_value=cluster), \
            mock.patch.object(main, "CB_PASSWORD", "test-password"):
        asyncio.run(enter_and_exit())
    return cluster


class HistoryIndexStartupTests(unittest.TestCase):
    def test_startup_creates_covering_history_index(self):
        cluster = run_lifespan_with_history_index_error(None)

        statements = [call.args[0] for call in cluster.query.call_args_list]
        self.assertTrue(any("CREATE INDEX idx_scan_history" in s for s in statements))

    def test_startup_continues_and_warns_when_index_creation_fails(self):
        with self.assertLogs(main.logger, level="WARNING") as logs:
            cluster = run_lifespan_with_history_index_error(CouchbaseException("boom"))

        self.assertIn("idx_scan_history", logs.output[0])
        cluster.close.assert_called_once()

    def test_existing_index_counts_as_success_without_warning(self):
        with self.assertNoLogs(main.logger, level="WARNING"):
            run_lifespan_with_history_index_error(QueryIndexAlreadyExistsException("exists"))


if __name__ == "__main__":
    unittest.main()
