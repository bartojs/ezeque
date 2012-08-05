(defproject ezeque "0.1.0-SNAPSHOT"
  :description "A lazy promise of events in the future"
  :url "http://github.com/bartonjustin/ezeque"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [net.java.dev.jna/jna "3.4.0"]]
  :jvm-opts [;"-Xdebug"
             ;"-Xrunjdwp:transport=dt_socket,server=y,suspend=n"
             "-Djna.library.path=/usr/local/lib"]
  :main ezeque.core)
