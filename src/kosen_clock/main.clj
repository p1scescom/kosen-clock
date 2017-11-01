(ns kosen-clock.main
  (:gen-class)
  (:import [org.opencv.core Core])
  (:require [clojure.java.io :as io]
            [duct.core :as duct]
            [kosen-clock.img :as img]
            [kosen-clock.handler.routing :as routing]
            [kosen-clock.handler.not-found :as not-found]
            [kosen-clock.port :as port]
            [selmer.parser :as parser]))

(def img-root (atom "img/show/"))
(def img-local (atom "img/local/"))
(def portn (atom 3000))

(defn setup [args]
  (println "Start Kosen Clock")

  (img/init)

  (when-let [access-root (get args "access-root")]
    (do (reset! routing/access-root access-root)
        (reset! not-found/access-root access-root)))

  (when-let [img-r (get args "img-root")]
    (do (reset! routing/img-root img-r)
        (reset! img-root img-r)))
  (when-not (.exists (io/as-file @img-root)) (.mkdirs (io/file @img-root)))
  (println "routing/img-root:" @img-root)

  (when-let [img-l (get args "img-local")]
    (do (reset! routing/img-local img-l)
        (reset! img-local img-l)))
  (when-not (.exists (io/as-file @img-local)) (.mkdirs (io/file @img-local)))

  (when-let [port-value (get args "port")]
    (let [port-long (Long/parseLong port-value)]
      (do (reset! port/port port-long)
          (reset! portn port-long))))
  (parser/set-resource-path! (io/resource "kosen_clock/"))

  (routing/init))

(defn -main [& {:as args}]
  (setup args)
  (let [duct-config (assoc (duct/read-config (io/resource "kosen_clock/config.edn"))
                          :duct.server.http/jetty {:port @portn})]
  (println duct-config)
  (duct/exec duct-config)))
