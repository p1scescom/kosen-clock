(ns kosen-clock.info
  (:require [integrant.core :as ig]))

(def access-root (atom ""))

(def img-root (atom "img/show"))

(def port (atom 3000))
