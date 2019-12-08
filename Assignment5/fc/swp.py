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
        self._timers = dict()
        self._buffer = dict()

    def send(self, data):
        for i in range(0, len(data), SWPPacket.MAX_DATA_SIZE):
            self._send(data[i:i+SWPPacket.MAX_DATA_SIZE])

    def _create_timer(self, seq_num):
        timer = threading.Timer(self._TIMEOUT, self._retransmit, [seq_num])
        timer.start()
        self._timers[seq_num] = timer
        logging.debug("Timer " + str(seq_num) + " created")

    def _transmit(self, seq_num):
        self._llp_endpoint.send(self._buffer[seq_num].to_bytes())

    def _send(self, data):
        # 1. Wait for a free space in the send window
        self._semaphore.acquire(blocking=True)

        # 2. Assign the chunk of data a sequence number
        seq_num = self._sequence_number
        self._sequence_number += 1

        # 3. Add the chunk of data to a buffer
        self._buffer[seq_num] = SWPPacket(
            type=SWPType.DATA,
            seq_num=seq_num,
            data=data
        )

        # 4. Start a retransmission timer
        self._create_timer(seq_num)

        # 5. Send the data in an SWP packet
        self._transmit(seq_num)


    def _retransmit(self, seq_num):
        logging.debug("Retransmit " + str(seq_num) + " called")
        self._create_timer(seq_num)
        self._transmit(seq_num)

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
            for i in list(self._timers.keys()):
                if i <= seq_num and i in self._timers:
                    self._timers.pop(i).cancel()
                    logging.debug("Timer " + str(i) + " cancalled")

            # 2. Discard that chunk of data.
            if seq_num in self._buffer:
                self._buffer.pop(seq_num)

            # 3. Signal that there is now a free space in the send window.
            self._semaphore.release()


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

        self._highest_acknowledged_sequence_number = 0
        self._buffer = dict()

    def send_ack(self):
        packet = SWPPacket(
            type=SWPType.ACK,
            seq_num=self._highest_acknowledged_sequence_number,
        )

        self._llp_endpoint.send(packet.to_bytes())

    def recv(self):
        return self._ready_data.get()

    def _recv(self):
        while True:
            # Receive data packet
            raw = self._llp_endpoint.recv()
            packet = SWPPacket.from_bytes(raw)
            logging.debug("Received: %s" % packet)

            if packet.type != SWPType.DATA:
                continue

            seq_num = packet.seq_num

            # 1. Check if the chunk of data was already acknowledged and
            #    retransmit an SWP ACK containing the highest acknowledged sequence number
            if seq_num < self._highest_acknowledged_sequence_number:
                self.send_ack()
                continue

            # 2. Add the chunk of data to a buffer
            self._buffer[seq_num] = packet.data

            # 3. Traverse the buffer
            seq_nums = sorted(self._buffer)
            for seq_num_1, seq_num_2 in zip(seq_nums, seq_nums[1:]):
                if seq_num_1 + 1 != seq_num_2:
                    self._highest_acknowledged_sequence_number = seq_num_1
                    break
                self._ready_data.put(self._buffer.pop(seq_num_1))

            # 4. Send an acknowledgement for the highest sequence number
            self.send_ack()





