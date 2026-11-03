from pydantic import BaseModel, Field


class SignUpRequest(BaseModel):
    fullName: str = Field(min_length=1, max_length=200)
    contact: str = Field(min_length=1, max_length=200)
    address: str = Field(default="", max_length=500)
    password: str = Field(min_length=4, max_length=128)
    plots: str = Field(default="3", max_length=32)
    hectares: str = Field(default="0.8", max_length=32)
    crops: str = Field(default="", max_length=500)


class SignInRequest(BaseModel):
    contact: str = Field(min_length=1, max_length=200)
    password: str = Field(min_length=1, max_length=128)


class ChangePasswordRequest(BaseModel):
    currentPassword: str = Field(min_length=1, max_length=128)
    newPassword: str = Field(min_length=4, max_length=128)


class UserProfile(BaseModel):
    userId: str
    fullName: str
    contact: str
    address: str
    plots: str
    hectares: str
    crops: str


class AuthResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
    user: UserProfile


class DetectionItem(BaseModel):
    label: str
    detail: str
    confidence: str
    confidence_raw: int


class Detections(BaseModel):
    disease: DetectionItem


class RecommendationItem(BaseModel):
    action: str
    urgency: str
    product: str


class Recommendations(BaseModel):
    disease: RecommendationItem
    general_advice: str


class PredictResponse(BaseModel):
    crop: str
    overall_accuracy: str
    status: str
    detections: Detections
    recommendations: Recommendations
