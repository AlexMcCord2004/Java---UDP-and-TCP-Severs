🖧 Java TCP & UDP Servers (Tux Machine Networking Lab)

This project includes two separate Java applications — a TCP Server/Client and a UDP Server/Client — designed to demonstrate core concepts of computer networking, specifically connection-oriented (TCP) and connectionless (UDP) communication.
Both programs were developed, compiled, and tested on Auburn University’s Tux Linux machines, simulating real-world distributed communication between two hosts over a network.

🧩 Overview

Each pair of programs (server and client) work together to establish communication between two different Tux machines:

One machine runs the server program, which listens for incoming connections or packets.

The other machine runs the client program, which sends requests and receives responses.

This setup allows for a hands-on understanding of how data is transmitted, received, and interpreted across separate systems using Java’s networking libraries.

⚙️ Project Structure
├── TCPServer.java
├── TCPClient.java
├── UDPServer.java
├── UDPClient.java
└── README.md

🧠 Key Concepts Demonstrated
🔹 TCP (Transmission Control Protocol)

Uses Java’s ServerSocket and Socket classes.

Establishes a reliable, ordered, connection-oriented data stream between client and server.

The server listens on a fixed port and waits for client connections.

Ideal for applications requiring guaranteed delivery, such as file transfer or remote login.

🔹 UDP (User Datagram Protocol)

Uses Java’s DatagramSocket and DatagramPacket classes.

Implements a connectionless, low-latency communication model.

Each message (datagram) is sent independently without establishing a session.

Ideal for fast data exchange where speed is more important than reliability (e.g., real-time streaming or sensor updates).
