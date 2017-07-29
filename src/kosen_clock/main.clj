(ns kosen-clock.main
  (:gen-class)
  (:require [clojure.java.io :as io]
            [duct.core :as duct]
            [kosen-clock.handler.routing :as routing]
            [kosen-clock.handler.not-found :as not-found]
            [kosen-clock.port :as port]
            [selmer.parser :as parser]))

(def img-root (atom "img/show/"))
(def portn (atom 3000))

(defn -main [& {:as args}]
  (println "Start Kosen Clock")
  (when-let [access-root (get args "access-root")]
    (dorun (reset! routing/access-root access-root)
        (reset! not-found/access-root access-root)))
  (when-let [img-r (get args "img-root")]
    (do (reset! routing/img-root img-r)
        (reset! img-root img-r)))
  (when-not (.exists (io/as-file @img-root)) (.mkdirs (io/file @img-root)))
  (println "routing/img-root:" @img-root)
  (when-let [port-value (Long/parseLong (get args "port"))]
    (do (reset! port/port port-value)
        (reset! portn port-value)))
  (parser/set-resource-path! (io/resource "kosen_clock/"))
  (let [duct-config (assoc (duct/read-config (io/resource "kosen_clock/config.edn")) :duct.server.http/jetty {:port @portn})]
    (println duct-config)
    (duct/exec duct-config)))
