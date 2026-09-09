@echo off
title SIH 2026 Email Threat ML Service
echo ===================================================================
echo Starting SIH 2026 Email Threat Intelligence & Clustering Service...
echo Swagger UI: http://127.0.0.1:8000/docs
echo ===================================================================
cd /d "%~dp0"
.\.venv\Scripts\uvicorn app:app --host 0.0.0.0 --port 8000 --reload
pause