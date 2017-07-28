(ns kosen-clock.handler.not-found
  (:require [clojure.java.io :as io]
            [compojure.route :as route]
            [integrant.core :as ig]
            [kosen-clock.info :as info]
            [selmer.parser :as parser]))

(def access-root (atom ""))

(defmethod ig/init-key :kosen-clock.handler/not-found [_ _]
  (parser/render-file "template/not-found.html" {:access-root @access-root}))
