from threading import Lock
import tornado
from tornado.netutil import TCPServer


class CbConnection(object):
    def __init__(self, stream, address, request_callback):
        print "Client connected: " + str(address)
        self.stream = stream
        self.address = address
        self.request_callback = request_callback
        self.read_message()

    def is_active(self):
        return not self.stream.closed()

    def read_message(self):
        self.stream.read_until("\n", self.data_callback)

    def send_message(self, text):
        print "to: %s --> %s" % (str(self.address), text)
        self.stream.write(text)

    def data_callback(self, data):
        print "from: %s <-- %s" % (str(self.address), data)
        self.request_callback(self, data)
        self.read_message()

class CbServer(TCPServer):

    def __init__(self, io_loop=None, ssl_options=None):
        super(CbServer, self).__init__(io_loop, ssl_options)
        self.connections = []
        self.lock = Lock()

    def handle_stream(self, stream, address):
        with self.lock:
            self.connections.append(
                CbConnection(stream, address, self.request_callback)
            )

    def request_callback(self, connection, data):
        with self.lock:
            self.connections[:] = [c for c in self.connections if c.is_active()]

        for c in self.connections:
            if c.is_active():  #c != connection and
                c.send_message(data)

def main():
    server = CbServer()
    server.listen(8888)
    tornado.ioloop.IOLoop.instance().start()

if __name__ == "__main__":
    main()
