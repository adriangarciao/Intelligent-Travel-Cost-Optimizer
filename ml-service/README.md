# ML Service (dummy)

This is a tiny FastAPI service providing deterministic dummy ML predictions for integration testing.

Run locally:

```bash
python -m venv .venv
.venv\Scripts\activate   # Windows PowerShell
pip install -r requirements.txt
uvicorn app:app --host 0.0.0.0 --port 8000
```

Endpoints:
- POST /predict/best-date-window
- POST /predict/option-recommendation

Requests and responses are simple JSON Pydantic models.
