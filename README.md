ezeque
======

*A lazy promise of events in the future*

This is just an experiment with zeromq and processing events via lazy-seqs.

# prerequisites

* You'll need zeromq 3.2 installed on your machine.

* on linux if libzmq isnt in /usr/local/lib then set jna.library.path to where libzmq is installed (see project.clj)

* (for now) on windows I renamed libzmq-v100-mt.dll to zmq.dll and set jna.library.path=C:\Program Files\ZeroMQ-3.2.0\bin

# example

Note that Im just using simple pubsub in zmq so this is prone to the slow-subsbriber problem described in the zguide.

    git clone https://github.com/bartonj/ezeque.git
    cd ezeque
    lein repl
    ;; start pubsub on port 5555
    ezeque.core=> (start)
    ...

    ;; test it works
    ezeque.core=> (raise :blabla)
    ...

    ;; start an event consumer that filters on even :myval
    ezeque.core=> (future (doseq [e (filter #(even? (:myval %)) (instream))]
                        (println "myfilter" (pr-str e))) 
                   (println "myfilter done"))
    ...

    ;; raise a whole lot of events to see consumer react
    ezeque.core=> (doseq [i (range 500)] (raise {:myevent :bla :myval i}))
    ...

    ;; you can start another repl subscribe to port 5555
    ;; for the same events as above but a different filter
    lein repl
    ezeque.core=> (future (doseq [e (filter #(odd? (:myval %)) (instream))]
                        (println "myfilter" (pr-str e))) 
                   (println "myfilter done"))
    ezeque.core=> (start nil ["tcp://localhost:5555"])
    ...
    ;; run the raise doseq again on the first repl


# licences

This is my understanding of the licences:

* ezeque &copy; 2012 Justin Barton - epl 1.0 (same as clojure)
* zeromq &copy; 2007-2011 iMatix - lgpl 3.0
* jna &copy; 2011 Timothy Wall - lgpl 2.1+
