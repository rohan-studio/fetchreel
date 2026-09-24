FROM python:3.11-slim

# ffmpeg is a system package, not a pip package — this is the whole reason
# this app needs the Docker runtime rather than Render's native Python one.
RUN apt-get update && \
    apt-get install -y --no-install-recommends ffmpeg && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

# Render's Docker services expect port 10000 and don't inject $PORT the
# way native runtimes do, so we bind to it directly. --timeout 300 stops
# gunicorn from killing a worker mid-download on a longer video. Threads
# (rather than only processes) give more concurrent downloads per MB of
# RAM, which matters on small instances — downloading is mostly waiting
# on network I/O, not CPU.
CMD ["gunicorn", "--workers", "2", "--threads", "4", "--worker-class", "gthread", \
     "--timeout", "300", "-b", "0.0.0.0:10000", "app:app"]
