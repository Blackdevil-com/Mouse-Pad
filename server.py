import socket
import pyautogui

HOST = "0.0.0.0"
PORT = 5007

# Tuned values
SENSITIVITY = 5.0   # Higher = faster cursor
ALPHA = 0.8        # Lower = less lag

pyautogui.FAILSAFE = False

sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
sock.bind((HOST, PORT))
sock.listen(1)

print(f"Listening on {HOST}:{PORT}")
conn, addr = sock.accept()
print(f"Connected by {addr}")

last_dx, last_dy = 0.0, 0.0  # smoothing memory

try:
    while True:
        data = conn.recv(1024)
        if not data:
            print("Client disconnected. Stopping server.")
            break

        data = data.decode().strip()
        for packet in data.splitlines():
            parts = packet.split(",")

            if parts[0] == "M" and len(parts) == 3:
                try:
                    dx = int(parts[1])
                    dy = int(parts[2])

                    # Apply smoothing
                    dx = ALPHA * dx + (1 - ALPHA) * last_dx
                    dy = ALPHA * dy + (1 - ALPHA) * last_dy
                    last_dx, last_dy = dx, dy

                    # Apply sensitivity
                    move_x = int(dx * SENSITIVITY)
                    move_y = int(dy * SENSITIVITY)

                    if move_x != 0 or move_y != 0:
                        pyautogui.moveRel(move_x, move_y, duration=0)

                except ValueError:
                    print("Invalid data:", parts)

            elif parts[0] == "LCLICK":
                pyautogui.click(button="left")

            elif parts[0] == "RCLICK":
                pyautogui.click(button="right")

            elif parts[0] == "DCLICK":
                pyautogui.doubleClick(button="left")

            elif parts[0] == "DRAG_START":
                pyautogui.mouseDown(button="left")

            elif parts[0] == "DRAG_MOVE" and len(parts) == 3:
                try:
                    dx = int(parts[1])
                    dy = int(parts[2])
                    move_x = int(dx * SENSITIVITY)
                    move_y = int(dy * SENSITIVITY)
                    pyautogui.moveRel(move_x, move_y, duration=0)
                except ValueError:
                    print("Invalid drag move:", parts)

            elif parts[0] == "DRAG_END":
                pyautogui.mouseUp(button="left")

except Exception as e:
    print("Error:", e)

finally:
    conn.close()
    sock.close()
    print("Server stopped.")
