package org.rackspace.gdeproxy

import java.util.concurrent.locks.ReentrantLock

import groovy.util.logging.Log4j;
import org.apache.log4j.Logger;

/**
 * The main class.
 */
@Log4j
class Deproxy {

  public static final String REQUEST_ID_HEADER_NAME = "Deproxy-Request-ID";
  def _messageChainsLock = new ReentrantLock()
  def _messageChains = [:]
  def _endpointLock = new ReentrantLock()
  def _endpoints = []
  def _defaultHandler = null
    def _default_client_connector = new BareClientConnector();

  public static final String VERSION = "0.9";
  public static final String VERSION_STRING = String.format("gdeproxy %s", VERSION);

  Deproxy(defaultHandler = null) {

    //    def __init__(self, default_handler=None):
    //        """
    //Params:
    //default_handler - An optional handler function to use for requests, if
    //not specified elsewhere
    //"""
    //        self._message_chains_lock = threading.Lock()
    //        self._message_chains = dict()
    //        self._endpoint_lock = threading.Lock()
    //        self._endpoints = []
    //        self.default_handler = default_handler
    _defaultHandler = defaultHandler
    //
  }

  public MessageChain makeRequest(Map params) {
    return makeRequest(
      params?.url,
      params?.method ?: "GET",
      params?.headers,
      params?.requestBody ?: "",
      params?.defaultHandler,
      params?.handlers,
      params?.addDefaultHeaders ?: true,
      params?.chunked ?: false
    );
  }
  public MessageChain makeRequest(
    String url,
    String method="GET",
    headers=null,
    String requestBody="",
    defaultHandler=null,
    handlers=null,
    addDefaultHeaders=true,
    boolean chunked=false) {
    //    def make_request(self, url, method='GET', headers=None, request_body='',
    //                     default_handler=None, handlers=None,
    //                     add_default_headers=True):
    //        """
    //Make an HTTP request to the given url and return a MessageChain.
    //
    //Parameters:
    //
    //url - The URL to send the client request to
    //method - The HTTP method to use, default is 'GET'
    //headers - A collection of request headers to send, defaults to None
    //request_body - The body of the request, as a string, defaults to empty
    //string
    //default_handler - An optional handler function to use for requests
    //related to this client request
    //handlers - A mapping object that maps endpoint references or names of
    //endpoints to handlers. If an endpoint or its name is a key within
    //``handlers``, all requests to that endpoint will be handled by the
    //associated handler
    //add_default_headers - If true, the 'Host', 'Accept', 'Accept-Encoding',
    //and 'User-Agent' headers will be added to the list of headers sent,
    //if not already specified in the ``headers`` parameter above.
    //Otherwise, those headers are not added. Defaults to True.
    //"""
    //        logger.debug('')

    log.debug "begin makeRequest"

    //
    //        if headers is None:
    //            headers = HeaderCollection()
    //        else:
    //            headers = HeaderCollection(headers)
    def data
    if (headers == null) {
      headers = new HeaderCollection()
    } else if (headers instanceof Map) {
      data = new HeaderCollection()
      for (String key : headers.keySet()) {
        data.add(key, headers[key])
      }
      headers = data
    } else if (headers instanceof HeaderCollection) {
      data = new HeaderCollection()
      for (Header header : headers){
        data.add(header.name, header.value)
      }
      headers = data
    }
    //
    //        request_id = str(uuid.uuid4())
    def requestId =  UUID.randomUUID().toString()
    //        if request_id_header_name not in headers:
    if (!headers.contains(REQUEST_ID_HEADER_NAME)){
      //            headers.add(request_id_header_name, request_id)
      headers.add(REQUEST_ID_HEADER_NAME, requestId)
    }
    //
    //        message_chain = MessageChain(default_handler=defadult_handler,
    //                                     handlers=handlers)
    def messageChain = new MessageChain(defaultHandler, handlers)
    //        self.add_message_chain(request_id, message_chain)
    addMessageChain(requestId, messageChain)
    //
    //        urlparts = list(urlparse.urlsplit(url, 'http'))
    //        scheme = urlparts[0]
    //        host = urlparts[1]
    //        urlparts[0] = ''
    //        urlparts[1] = ''
    //        path = urlparse.urlunsplit(urlparts)
    def uri = new URI(url)
    def host = uri.host
    def port = uri.port
    boolean https = (uri.scheme == 'https');
    def path = uri.path
    //
    //        logger.debug('request_body: "{0}"'.format(request_body))
    log.debug "request body: ${requestBody}"

    //        if len(request_body) > 0:
    if (requestBody && requestBody.length() > 0) {
      //            headers.add('Content-Length', len(request_body))
      headers.add("Content-Length", requestBody.length())
    }
    //
    //        if add_default_headers:
    if (addDefaultHeaders){
      //            if 'Host' not in headers:
      //                headers.add('Host', host)
      if (!headers.contains("Host")){
        headers.add("Host", host)
      }
      //            if 'Accept' not in headers:
      //                headers.add('Accept', '*/*')
      if (!headers.contains("Accept")){
        headers.add("Accept", "*/*")
      }
      //            if 'Accept-Encoding' not in headers:
      //                headers.add('Accept-Encoding',
      //                            'identity, deflate, compress, gzip')
      if (!headers.contains("Accept-Encoding")){
        headers.add("Accept-Encoding", "identity")
      }
      //            if 'User-Agent' not in headers:
      //                headers.add('User-Agent', version_string)
      if (!headers.contains("User-Agent")){
        headers.add("User-Agent", VERSION_STRING)
      }
      //
    }

    Request request = new Request(method, path, headers, requestBody)

    RequestParams requestParams = new RequestParams()
    requestParams.usedChunkedTransferEncoding = chunked;
    requestParams.sendDefaultRequestHeaders = addDefaultHeaders;

    log.debug "calling sendRequest"
    Response response = this._default_client_connector.sendRequest(request, https, host, port, requestParams)
    log.debug "back from sendRequest"
    //
    //        self.remove_message_chain(request_id)
    removeMessageChain(requestId)
    //
    //        message_chain.sent_request = request
    messageChain.sentRequest = request
    //        message_chain.received_response = response
    messageChain.receivedResponse = response
    //
    //        return message_chain
    //
    log.debug "end makeRequest"

    return messageChain
  }

  //    def add_endpoint(self, port, name=None, hostname=None,
  //                     default_handler=None):
  def addEndpoint(int port, name=null, hostname=null, defaultHandler=null) {
    //        """Add a DeproxyEndpoint object to this Deproxy object's list of
    //endpoints, giving it the specified server address, and then return the
    //endpoint.
    //
    //Params:
    //port - The port on which the new endpoint will listen
    //name - An optional descriptive name for the new endpoint. If None, a
    //suitable default will be generated
    //hostname - The ``hostname`` portion of the address tuple passed to
    //``socket.bind``. If not specified, it defaults to 'localhost'
    //default_handler - An optional handler function to use for requests that
    //the new endpoint will handle, if not specified elsewhere
    //"""
    //
    //        logger.debug('')
    //        endpoint = None
    def endpoint = null
    //        with self._endpoint_lock:
    synchronized(_endpointLock) {
      //            if name is None:
      if (name == null) {
        //                name = 'Endpoint-%i' % len(self._endpoints)
        name = String.format("Endpoint-%d", _endpoints.size())
      }
      //            endpoint = DeproxyEndpoint(self, port=port, name=name,
      //                                       hostname=hostname,
      //                                       default_handler=default_handler)
      endpoint = new DeproxyEndpoint(this, port, name, hostname, defaultHandler)
      //            self._endpoints.append(endpoint)
      _endpoints.add(endpoint)
      //            return endpoint
      return endpoint
      //
    }
  }

  //    def _remove_endpoint(self, endpoint):
  def _remove_endpoint(endpoint) {
    //        """Remove a DeproxyEndpoint from the list of endpoints. Returns True if
    //the endpoint was removed, or False if the endpoint was not in the list.
    //This method should normally not be called by user code. Instead, call
    //the endpoint's shutdown method."""
    //        logger.debug('')

    //        with self._endpoint_lock:
    synchronized (_endpointLock) {
      //            count = len(self._endpoints)
      count = _endpoints.size()
      //            self._endpoints = [e for e in self._endpoints if e != endpoint]
      _endpoints = _endpoints.findAll { e -> e != endpoint }
      //            return (count != len(self._endpoints))
      return (count != _endpoints.size())
      //
    }
  }

  //    def shutdown_all_endpoints(self):
  def shutdown() {
    //        """Shutdown and remove all endpoints in use."""
    //        logger.debug('')
    synchronized (_endpointLock) {
      for (e in _endpoints) {
        e.shutdown()
      }
      _endpoints = []
    }
  }

  //    def add_message_chain(self, request_id, message_chain):
  def addMessageChain(requestId, messageChain) {
    //        """Add a MessageChain to the internal list for the given request ID."""
    //        logger.debug('request_id = %s' % request_id)
    //        with self._message_chains_lock:
    synchronized (_messageChainsLock) {
      //            self._message_chains[request_id] = message_chain
      _messageChains[requestId] = messageChain
      //
    }
  }

  //    def remove_message_chain(self, request_id):
  def removeMessageChain(requestId) {
    //        """Remove a particular MessageChain from the internal list."""
    //        logger.debug('request_id = %s' % request_id)
    //        with self._message_chains_lock:
    synchronized (_messageChainsLock) {
      //            del self._message_chains[request_id]
      _messageChains.remove(requestId)
      //
    }
  }

  //    def get_message_chain(self, request_id):
  def getMessageChain(requestId) {
    //        """Return the MessageChain for the given request ID."""
    //        logger.debug('request_id = %s' % request_id)
    //        with self._message_chains_lock:
    synchronized (_messageChainsLock) {
      //            if request_id in self._message_chains:
      if (_messageChains.containsKey(requestId)) {
        //                return self._message_chains[request_id]
        return _messageChains[requestId]
        //            else:
      } else {
        //                return None
        return null
        //
      }
    }
  }

  //    def add_orphaned_handling(self, handling):
  def addOrphanedHandling(handling) {
    //        """Add the handling to all available MessageChains."""
    //        logger.debug('Adding orphaned handling')
    //        with self._message_chains_lock:
    synchronized (_messageChainsLock) {
      //            for mc in self._message_chains.itervalues():
      for (mc in _messageChains.values()) {
        //                mc.add_orphaned_handling(handling)
        mc.addOrphanedHandling(handling)
      }
    }
  }

  //
  //def read_body_from_stream(stream, headers):
  static String readBody(reader, headers) {
  
      if (headers == null)
          return null
          
    Logger log = Logger.getLogger(Deproxy.class.getName());

      if (headers == null)
          return reader
    //    if ('Transfer-Encoding' in headers and
    //            headers['Transfer-Encoding'] != 'identity'):
    //        # 2
    //        logger.debug('NotImplementedError - Transfer-Encoding != identity')
    //        raise NotImplementedError
    headers.findAll("Transfer-Encoding").each {
      if (it.value != "identity")
      {
          log.error "Non-identity transfer encoding, not yet supported in GDeproxy.  Unable to read response body."
          return null
      }
    }
    //    elif 'Content-Length' in headers:
    //        # 3
    //        length = int(headers['Content-Length'])
    //        body = stream.read(length)
    if (headers.contains("Content-Length")) {
      int length = headers.getFirstValue("Content-Length").toInteger();
      log.debug("Headers contain Content-Length: ${length}")
      //TODO: this is reading characters, but according to the spec, Content-Length is a count of octets.
      char[] data = new char[length]
      int count = reader.read(data, 0, length)
      if (count != length) {
        // TODO: what does the spec say should happen in this case?
      }
      return new String(data)
    }
    //    elif False:
    //        # multipart/byteranges ?
    //        logger.debug('NotImplementedError - multipart/byteranges')
    //        raise NotImplementedError

    //    else:
    //        # there is no body
    //        body = None
    //    return body
    log.debug("Returning null");
    return null
  }
}