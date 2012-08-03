(ns ezeque.ipc
  (:refer-clojure :exclude [send])
  (:import [com.sun.jna Native Function Pointer Memory]
           [java.nio ByteBuffer ByteOrder]))

(defn- call [f ret & args] (.invoke (Function/getFunction "zmq" (name f)) ret (to-array args)))
(defn- zmsg [] (-> 32 ByteBuffer/allocateDirect (.order ByteOrder/LITTLE_ENDIAN) Native/getDirectBufferPointer))
(def sockettypes {:PUB 1 :SUB 2 :REQ 3 :REP 4}) 
(defn zerr [] (let [err (call :zmq_errno Integer)]
                (str "[" err "] " (call :zmq_strerror String err)))) 

(defn init [] (call :zmq_ctx_new Pointer))
(defn destroy [ctx] (when ctx (call :zmq_ctx_destroy Integer)))

(defn socket [ctx stype bindings]
  (let [sock (call :zmq_socket Pointer ctx (.intValue (stype sockettypes)))]
    (if (= stype :REQ)
      (doseq [b bindings] (call :zmq_connect Integer sock b))
      (doseq [b bindings] (call :zmq_bind Integer sock b)))
    sock))
(defn close [sock stype bindings]
  (when sock
    ;;(let [stype (call :zmq_getsockopt Integer sock (.intValue 16))]
    ;;(if (= stype (:REQ sockettypes))
    (if (= stype :REQ)
        (doseq [b bindings] (call :zmq_disconnect Integer sock b))
        (doseq [b bindings] (call :zmq_unbind Integer sock b)))
      (call :zmq_close Integer sock)))

(defn send [sock str]
  (let [req (zmsg)
        mem (doto (Memory. (inc (count str))) (.setString 0 str false))]
  (call :zmq_msg_init_data Integer req mem (.longValue (count str)) nil nil)
  (call :zmq_msg_send Integer req sock (.intValue 0))
  (call :zmq_msg_close Integer req)))
  
(defn recv [sock]
  (let [req (zmsg)
        i1 (call :zmq_msg_init Integer req)
        r (call :zmq_msg_recv Integer req sock (.intValue 0))]
    (println (str "recv (" r ") " (zerr)))
    (when (> r 0)
      (let [payload (call :zmq_msg_data Pointer req)
            resp (String. (.getByteArray payload 0 r))
            c1 (call :zmq_msg_close Integer req)]
            (println (str  "recv '"  resp "'")) 
            resp))))

(def zctx (atom nil))
;(def insocket (atom nil))
(def outsocket (atom nil))
(def outconnects (atom nil))

(defn now [] (.getTime (java.util.Date.)))

;;TODO: validate the event string before reading it
;; socket needs to be created/used/closed in same thread 
(defn- start-incoming [binds deliverer]
  (future
    (let [started (now)
          sock (socket @zctx :REP binds)
          process (fn [] (when (< (- (now) started) 60000)
                          (let [s (recv sock)]
                            (println (str "incomming: " s))
                            (when-not (or (nil? s) (empty? (.trim s)) (= s ":quit"))
                              (println (str "incoming RECV: '" s "'"))                              
                              (deliverer (read-string s))
                              true))))] 
      (while (process) (println "RECV listening..."))
      (close sock :REP binds)
      (println "RECV done."))))

(defn stop []
  (close @outsocket :REQ @outconnects)
  ;(when @insocket (close @insocket))
  (destroy @zctx))

(defn start [binds connects deliverer]
  (stop)
  (reset! zctx (init))
  (reset! outsocket (socket @zctx :REQ connects))
  (reset! outconnects connects) 
  ;(reset! insocket (socket @zctx :REP binds))
  (start-incoming binds deliverer))

(defn send-out [str]
  (when @outsocket (send @outsocket str)))
