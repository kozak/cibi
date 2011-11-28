import socket
import tornado
import tornado.iostream


class Client(object):

    def create(self):
        self.s = socket.socket()
        self.stream = tornado.iostream.IOStream(self.s)
        self.stream.connect(('localhost', 8888), self.send_request)

    def send_request(self, data):
        content_size = len(data)
        self.stream.write("Content-Length:%s\r\n\r\n%s" % (content_size, str(data)))
        self.stream.read_until("\r\n\r\n", self.on_headers)

    def on_headers(self, data):
        print "On headers: %s " % data
        headers = {}
        for line in data.split("\r\n"):
           parts = line.split(":")
           if len(parts) == 2:
               headers[parts[0].strip()] = parts[1].strip()
        self.stream.read_bytes(int(headers["Content-Length"]), self.on_body)

    def on_body(self, data):
        print data
        self.stream.close()
        tornado.ioloop.IOLoop.instance().stop()



def main():
    c = Client()
    c.create()
    c.send_request("DUPA!")


if __name__ == "__main__":
    main()
