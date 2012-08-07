(ns ezeque.ipc
  (:refer-clojure :exclude [send])
  (:import [com.sun.jna Native Function Pointer Memory]
           [java.nio ByteBuffer ByteOrder]))

 
(defn- invoke [lib func ret & args]
  (.invoke (Function/getFunction lib func) ret (to-array args)))

(defn zerr [] (let [errno (invoke "zmq" "zmq_errno" Integer)]
                (str "[" errno "] " (invoke "zmq" "zmq_strerror" String errno))))

(defn- call [func ret & args]
  (let [rc (apply invoke "zmq" (name func) ret args)]
    (when (or (and (= Integer ret) (< rc 0))
              (and (= Pointer ret) (or (nil? rc) (identical? rc Pointer/NULL))))
      (throw (Exception. (format "Error %d calling zmq/%s %s : %s"
                                 rc (name func) (pr-str args) (zerr)))))
    rc))

(defn- memstr [s] (doto (Memory. (inc (count s))) (.setString 0 s false)))
;;(defn- zmsg [] (-> 32 ByteBuffer/allocateDirect (.order ByteOrder/LITTLE_ENDIAN) Native/getDirectBufferPointer))

(defn- zmsg [] (Memory. 32))
(def sockettypes {:PUB 1 :SUB 2 :REQ 3 :REP 4}) 
(def socketopts {:SUBSCRIBE 6})

(defn context [] (call :zmq_ctx_new Pointer))
(defn destroy [ctx] (when ctx (call :zmq_ctx_destroy Integer)))

(defn socket [ctx stype addrs & opts]
  (let [sock (call :zmq_socket Pointer ctx (.intValue (stype sockettypes)))]
    (when (= stype :SUB)
      (let [subfilter (str (:SUBSCRIBE (into {} (map vec (partition 2 opts)))))]
          (call :zmq_setsockopt Integer sock (.intValue (:SUBSCRIBE socketopts))
                (memstr subfilter)
                (.longValue (count subfilter)))))
    (cond
     (#{:SUB :REQ} stype)
     (doseq [addr addrs] (call :zmq_connect Integer sock addr))
     (#{:PUB :REP} stype)
     (doseq [addr addrs] (call :zmq_bind Integer sock addr))
     :else nil)
    sock))

(defn close [sock stype addrs]
  (when sock
    (cond
       (#{:PUB :REP} stype)
          (doseq [addr addrs] (call :zmq_unbind Integer sock addr))
       (#{:SUB :REQ} stype)
          (doseq [addr addrs] (call :zmq_disconnect Integer sock addr))
     )
     (call :zmq_close Integer sock)))

(defn send [sock str]
  (let [msg (zmsg)
        _ (call :zmq_msg_init_size Integer msg (.longValue (count str)))
        data (call :zmq_msg_data Pointer msg)
        sbytes (.getBytes str)]
    ;;     (.write data (long 0) sbytes 0 (count sbytes))
    (invoke "c" "memcpy" Void data sbytes (.longValue (count sbytes)))
    (call :zmq_msg_send Integer msg sock (.intValue 0))
    (call :zmq_msg_close Integer msg)
  (count sbytes)))

(defn recv [sock]
  (let [msg (zmsg)
        _ (call :zmq_msg_init Integer msg)
        n (call :zmq_msg_recv Integer msg sock (.intValue 0))
        payload (call :zmq_msg_data Pointer msg)
        smsg (String. (.getByteArray payload 0 n))]
    ;(invoke "c" "free" Void payload)
    (call :zmq_msg_close Integer msg)
    smsg))


