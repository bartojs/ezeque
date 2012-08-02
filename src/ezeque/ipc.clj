(ns ezeque.ipc
  (:refer-clojure :exclude [send])
  (:import [com.sun.jna Native Function Pointer Memory]
           [java.nio ByteBuffer ByteOrder]))

(defn- call [f ret & args] (.invoke (Function/getFunction "zmq" (name f)) ret (to-array args)))
(defn- zmsg [] (-> 32 ByteBuffer/allocateDirect (.order ByteOrder/LITTLE_ENDIAN) Native/getDirectBufferPointer))
(def sockettypes {:PUB 1 :SUB 2 :REQ 3 :REP 4}) 

(defn init [] (call :zmq_ctx_new Pointer))
(defn destroy [ctx] (call :zmq_ctx_destroy Integer))
(defn socket [ctx stype bindings & opts]
  (let [sock (call :zmq_socket Pointer ctx (.intValue (stype sockettypes)))]
    (if (= stype :REQ)
      (doseq [b bindings] (call :zmq_connect Integer sock b))
      (doseq [b bindings] (call :zmq_bind Integer sock b)))
    sock))
(defn close [sock] (call :zmq_msg_close Integer))

(defn send [sock str]
  (let [req (zmsg)
        mem (doto (Memory. (inc (count str))) (.setString 0 str false))]
  (call :zmq_msg_init_data Integer req mem (.longValue (count str)) nil nil)
  (call :zmq_msg_send Integer req sock (.intValue 0))
  (call :zmq_msg_close Integer req)))
  
(defn recv [sock]
  (let [req (zmsg)]
    (call :zmq_msg_init Integer req)
    (call :zmq_msg_recv Integer req sock (.intValue 0))
    (let [sz (call :zmq_msg_size Integer req)
          payload (call :zmq_msg_data Pointer req)
          resp (String. (.getByteArray payload 0 sz))]
      (call :zmq_msg_close Integer req)
      resp)))

(def zctx (atom nil))
(def insocket (atom nil))
(def outsocket (atom nil))

;;TODO: validate the event string before reading it
(defn- start-incoming [deliverer]
  (future
    (let [process (fn [] (let [str (recv @insocket)]
                          (when (not= str ":quit")
                            (println "incoming RECV: '" str "'")
                            (deliverer (read-string str))
                            true)))] 
        (while (process))
        (when @insocket (close @insocket))
        (reset! insocket nil))))

(defn stop []
  (when @outsocket (close @outsocket))
  (when @insocket (close @insocket))
  (when @zctx (destroy @zctx)))

(defn start [binds connects deliverer]
  (stop)
  (reset! zctx (init))
  (reset! outsocket (socket @zctx :REQ connects)) 
  (reset! insocket (socket @zctx :REP binds))
  (start-incoming deliverer))

(defn send-out [str]
  (when @outsocket (send @outsocket str)))
