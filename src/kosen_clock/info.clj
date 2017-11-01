(ns kosen-clock.info
  (:require [integrant.core :as ig]))

(def access-root (atom ""))

(def img-root (atom "img/show/"))

(def img-local (atom "img/local/"))

(def portn (atom 3000))
