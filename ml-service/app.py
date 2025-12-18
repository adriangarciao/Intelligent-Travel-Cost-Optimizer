from fastapi import FastAPI
from pydantic import BaseModel
from datetime import date
from typing import Optional

app = FastAPI(title="TravelOptimizer ML (dummy)")

class BestDateWindowRequest(BaseModel):
    origin: str
    destination: str
    earliestDepartureDate: date
    latestDepartureDate: date
    earliestReturnDate: Optional[date] = None
    latestReturnDate: Optional[date] = None
    maxBudget: Optional[float] = None
    numTravelers: Optional[int] = 1
    preferences: Optional[dict] = None

class BestDateWindowResponse(BaseModel):
    recommendedDepartureDate: date
    recommendedReturnDate: Optional[date] = None
    confidence: float

class OptionRecommendationRequest(BaseModel):
    price: float
    currency: Optional[str] = "USD"
    departureDate: date
    returnDate: Optional[date] = None
    stops: Optional[int] = 0
    durationMinutes: Optional[int] = None
    numTravelers: Optional[int] = 1
    maxBudget: Optional[float] = None

class OptionRecommendationResponse(BaseModel):
    isGoodDeal: bool
    priceTrend: str
    note: Optional[str] = None

@app.post("/predict/best-date-window", response_model=BestDateWindowResponse)
def predict_best_date_window(req: BestDateWindowRequest):
    # deterministic midpoint logic
    start = req.earliestDepartureDate
    end = req.latestDepartureDate
    mid = date.fromordinal((start.toordinal() + end.toordinal()) // 2)

    # returnDate: pick midpoint if return dates present
    rec_return = None
    if req.earliestReturnDate and req.latestReturnDate:
        rstart = req.earliestReturnDate
        rend = req.latestReturnDate
        rec_return = date.fromordinal((rstart.toordinal() + rend.toordinal()) // 2)

    # confidence heuristic: higher if maxBudget is large
    confidence = 0.5
    if req.maxBudget:
        if req.maxBudget >= 2000:
            confidence = 0.9
        elif req.maxBudget >= 1000:
            confidence = 0.7
        else:
            confidence = 0.6

    return BestDateWindowResponse(
        recommendedDepartureDate=mid,
        recommendedReturnDate=rec_return,
        confidence=confidence
    )

@app.post("/predict/option-recommendation", response_model=OptionRecommendationResponse)
def predict_option_recommendation(req: OptionRecommendationRequest):
    is_good = False
    if req.maxBudget and req.price <= req.maxBudget:
        is_good = True

    # simple priceTrend rule
    price_trend = "stable"
    if req.price > (req.maxBudget or req.price):
        price_trend = "rising"

    note = None
    if not is_good:
        note = "Price exceeds budget"
    else:
        note = "Meets budget criteria"

    return OptionRecommendationResponse(isGoodDeal=is_good, priceTrend=price_trend, note=note)
