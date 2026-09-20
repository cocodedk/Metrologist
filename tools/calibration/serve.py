"""Serve only the project's static tools on localhost for calibration/browser checks."""
import argparse
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--port', type=int, default=8766)
args = parser.parse_args()
handler = partial(SimpleHTTPRequestHandler, directory=str(Path(__file__).resolve().parents[1]))
server = ThreadingHTTPServer(('127.0.0.1', args.port), handler)
print(f'Calibration target: http://127.0.0.1:{args.port}/calibration-target.html', flush=True)
try:
    server.serve_forever()
except KeyboardInterrupt:
    pass
finally:
    server.server_close()
