import enum
import logging
import queue
import struct
import threading
import llp


class SWPType(enum.IntEnum):
    DATA = ord('D')
    ACK = ord('A')

class SWPPacket:
    _PACK_FORMAT = '!BI'
    _HEADER_SIZE = struct.calcsize(_PACK_FORMAT)
    MAX_DATA_SIZE = 1400 # Leaves plenty of space for IP + UDP + SWP header

    def __init__(self, type, seq_num, data=b''):
        self._type = type
        self._seq_num = seq_num
        self._data = data

    @property
    def type(self):
        return self._type

    @property
    def seq_num(self):
        return self._seq_num

    @property
    def data(self):
        return self._data

    def to_bytes(self):
        header = struct.pack(SWPPacket._PACK_FORMAT, self._type.value,
                self._seq_num)
        return header + self._data

    @classmethod
    def from_bytes(cls, raw):
        header = struct.unpack(SWPPacket._PACK_FORMAT,
                raw[:SWPPacket._HEADER_SIZE])
        type = SWPType(header[0])
        seq_num = header[1]
        data = raw[SWPPacket._HEADER_SIZE:]
        return SWPPacket(type, seq_num, data)

    def __str__(self):
        return "%s %d %s" % (self._type.name, self._seq_num, repr(self._data))

class SWPSender:
    _SEND_WINDOW_SIZE = 5
    _TIMEOUT = 1

    def __init__(self, remote_address, loss_probability=0):
        self._llp_endpoint = llp.LLPEndpoint(remote_address=remote_address,
                loss_probability=loss_probability)

        # Start receive thread
        self._recv_thread = threading.Thread(target=self._recv)
        self._recv_thread.start()

        self._sequence_number = 0
        self._semaphore = threading.Semaphore(self._SEND_WINDOW_SIZE)
        self.timers = dict()
        self.buffer = dict()

    def send(self, data):
        for i in range(0, len(data), SWPPacket.MAX_DATA_SIZE):
            self._send(data[i:i+SWPPacket.MAX_DATA_SIZE])

    def _create_timer(self, seq_num):
        timer = threading.Timer(self._TIMEOUT, self._retransmit, [seq_num])
        timer.start()
        self.timers[seq_num] = timer

    def _transmit(self, seq_num):
        self._llp_endpoint.send(self.buffer[seq_num])

    def _send(self, data):
        # 1. Wait for a free space in the send window
        self._semaphore.acquire(blocking=True)

        # 2. Assign the chunk of data a sequence number
        seq_num = self._sequence_number
        self._sequence_number += 1

        # 3. Add the chunk of data to a buffer
        self.buffer[seq_num] = SWPPacket(
            type=SWPType.DATA,
            seq_num=seq_num,
            data=data
        )

        # 4. Send the data in an SWP packet
        self._transmit(seq_num)

        # 5. Start a retransmission timer
        self._create_timer(seq_num)

    def _retransmit(self, seq_num):
        self._transmit(seq_num)
        self._create_timer(seq_num)
        return

    def _recv(self):
        while True:
            # Receive SWP packet
            raw = self._llp_endpoint.recv()
            if raw is None:
                continue
            packet = SWPPacket.from_bytes(raw)
            logging.debug("Received: %s" % packet)

            if packet.type != SWPType.ACK:
                continue

            seq_num = packet.seq_num

            # 1. Cancel the retransmission timer for that chunk of data.
            if seq_num in self.timers:
                self.timers.pop(seq_num).cancel()

            # 2. Discard that chunk of data.
            if seq_num in self.buffer:
                self.buffer.pop(seq_num)

            # 3. Signal that there is now a free space in the send window.
            self._semaphore.release()

        return

class SWPReceiver:
    _RECV_WINDOW_SIZE = 5

    def __init__(self, local_address, loss_probability=0):
        self._llp_endpoint = llp.LLPEndpoint(local_address=local_address,
                loss_probability=loss_probability)

        # Received data waiting for application to consume
        self._ready_data = queue.Queue()

        # Start receive thread
        self._recv_thread = threading.Thread(target=self._recv)
        self._recv_thread.start()

        # TODO: Add additional state variables


    def recv(self):
        return self._ready_data.get()

    def _recv(self):
        while True:
            # Receive data packet
            raw = self._llp_endpoint.recv()
            packet = SWPPacket.from_bytes(raw)
            logging.debug("Received: %s" % packet)

            # TODO

        return
