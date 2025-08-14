FROM python:3.13.6

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY consumer.py .
RUN mkdir /data

CMD ["python", "-u", "consumer.py"]