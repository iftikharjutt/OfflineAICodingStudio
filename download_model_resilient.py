import os
import sys
import time
import urllib.request
import urllib.error
import socket

def download_file_resilient(url, destination_path, chunk_size=256*1024):
    """
    Downloads a large file with keep-alive socket optimization and automatic resume support.
    """
    dest_dir = os.path.dirname(destination_path)
    if dest_dir:
        os.makedirs(dest_dir, exist_ok=True)

    # Set default socket timeout for fast reconnection
    socket.setdefaulttimeout(15)

    total_size = -1
    try:
        req = urllib.request.Request(url, method='HEAD', headers={
            'User-Agent': 'Mozilla/5.0 (Android; Termux)',
            'Connection': 'keep-alive'
        })
        with urllib.request.urlopen(req) as resp:
            total_size = int(resp.headers.get('Content-Length', -1))
            print(f"[Info] Target total file size: {total_size / (1024*1024):.2f} MB")
    except Exception as e:
        print(f"[Warning] Could not fetch total size: {e}")

    retry_count = 0
    while True:
        existing_size = os.path.getsize(destination_path) if os.path.exists(destination_path) else 0

        if total_size > 0 and existing_size >= total_size:
            print(f"\n[Success] Download complete! File saved to: {destination_path} ({existing_size / (1024*1024):.2f} MB)")
            break

        headers = {
            'User-Agent': 'Mozilla/5.0 (Android; Termux)',
            'Connection': 'keep-alive',
            'Accept-Encoding': 'identity'
        }
        if existing_size > 0:
            headers['Range'] = f'bytes={existing_size}-'
            print(f"[Resume] Connection dropped or stabilized. Resuming from byte offset: {existing_size / (1024*1024):.2f} MB...")

        try:
            req = urllib.request.Request(url, headers=headers)
            with urllib.request.urlopen(req, timeout=15) as resp, open(destination_path, 'ab') as out_file:
                start_time = time.time()
                bytes_downloaded_this_session = 0

                while True:
                    chunk = resp.read(chunk_size)
                    if not chunk:
                        break
                    out_file.write(chunk)
                    out_file.flush()
                    
                    bytes_downloaded_this_session += len(chunk)
                    current_total = existing_size + bytes_downloaded_this_session
                    
                    elapsed = time.time() - start_time
                    speed_mbps = (bytes_downloaded_this_session / (1024 * 1024)) / elapsed if elapsed > 0 else 0

                    if total_size > 0:
                        pct = (current_total / total_size) * 100
                        print(f"\rProgress: {pct:.2f}% | Downloaded: {current_total / (1024*1024):.2f} / {total_size / (1024*1024):.2f} MB | Speed: {speed_mbps:.2f} MB/s", end="", flush=True)

            final_size = os.path.getsize(destination_path)
            if total_size > 0 and final_size >= total_size:
                print(f"\n\n[Success] Download completed cleanly: {destination_path}")
                break

        except (urllib.error.URLError, urllib.error.HTTPError, TimeoutError, ConnectionError, OSError, socket.timeout) as e:
            retry_count += 1
            print(f"\n[Network Signal Reconnecting] Reason: {e}. Reconnecting immediately (Attempt #{retry_count})...")
            time.sleep(2)

if __name__ == "__main__":
    if len(sys.argv) < 2:
        model_url = "https://huggingface.co/Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF/resolve/main/qwen2.5-coder-1.5b-instruct-q4_k_m.gguf"
        target_path = "/data/data/com.termux/files/home/OfflineAICodingStudio/Workspace/Models/qwen2.5-coder-1.5b-instruct-q4_k_m.gguf"
    else:
        model_url = sys.argv[1]
        target_path = sys.argv[2] if len(sys.argv) > 2 else "/data/data/com.termux/files/home/OfflineAICodingStudio/Workspace/Models/model.gguf"

    download_file_resilient(model_url, target_path)
