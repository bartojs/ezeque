(ns ezeque.ipc
  (:refer-clojure :exclude [send])
  (:require [clojure.java.io :as io] [qbits.jilch.mq :as jilch])
  )

:: TODO use jilch instead of zmq via jna

(def sockettypes {:PUB jilch/pub :SUB jilch/sub :REQ jilch/req :REP jilch/rep}) 
(def socketopts {:SUBSCRIBE 6})

(defn context [] (jilch/context 1))
(defn destroy [ctx] (when ctx (.term ctx)))

(defn socket [ctx stype addrs & opts]
  (let [sock (jilch/socket ctx (get sockettypes stype))]
    (when (= stype :SUB)
      (let [subfilter (str (:SUBSCRIBE (into {} (map vec (partition 2 opts)))))]
          (jilch/subscribe sock subfilter)))
    (cond
     (contains? #{:SUB :REQ} stype)
     (doseq [addr addrs] (jilch/connect sock addr))
     (contains? #{:PUB :REP} stype)
     (doseq [addr addrs] (jilch/bind sock addr))
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
        sbytes (.getBytes str)
        nbytes (.longValue (count sbytes))]
    (call :zmq_msg_init_size Integer msg nbytes)
    (doto (call :zmq_msg_data Pointer msg)
       (.write 0 sbytes 0 nbytes))
    (call :zmq_msg_send Integer msg sock (.intValue 0))
    (call :zmq_msg_close Integer msg)
    nbytes))

(defn recv [sock]
  (let [msg (zmsg)
        _ (call :zmq_msg_init Integer msg)
        n (call :zmq_msg_recv Integer msg sock (.intValue 0))
        payload (call :zmq_msg_data Pointer msg)
        smsg (String. (.getByteArray payload 0 n))]
    (call :zmq_msg_close Integer msg)
    smsg))


