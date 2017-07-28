(ns kosen-clock.port
  (:require [kosen-clock.info :as info]
            [integrant.core :as ig]))

(def port (atom 3000))

;(defn get-port []
;  @port)

; (derive :kosen-clock/port :duct/module)

(defmethod ig/init-key :kosen-clock/port [_ _]
  @port)

; {:req #{:duct.server.http/jetty}
;  :fn (fn [config]
;        (assoc-in config [:duct.server.http/jetty :port] @port))}

; {:req #{:duct.server.http/jetty}
;  :fn (fn [config]
;        (assoc-in config [:duct.server.http/jetty :port] @info/port))}
