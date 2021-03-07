(ns deathstar.test.docker
  (:gen-class)
  (:require
   [clojure.core.async :as a :refer [chan go go-loop <! >! <!! >!!  take! put! offer! poll! alt! alts! close!
                                     pub sub unsub mult tap untap mix admix unmix pipe
                                     timeout to-chan  sliding-buffer dropping-buffer
                                     pipeline pipeline-async]]
   [clojure.string]
   [clojure.spec.alpha :as s]
   [clojure.java.io]
   [clojure.java.shell]
   
   [clj-docker-client.core :as docker]))

(defonce ^:private registry-ref (atom {}))

(defn create-opts
  [{:keys [::id] :or {id :main} :as opts}]
  (let [suffix (str "-" (name id))]
    (merge
     {::image-name "dgraph/dgraph:v20.11.2"
      ::volume-name (str "deathstar-dgraph" suffix)
      ::network-name (str "deathstar-network" suffix)
      ::zero-name (str "deathstar-dgraph-zero" suffix)
      ::alpha-name (str "deathstar-dgraph-alpha" suffix)
      ::ratel-name (str "deathstar-dgraph-ratel" suffix)
      ::alpha-port 3088
      ::ratel-port 8000
      ::remove-volume? false}
     opts)))

(def peer1-preset (create-opts
                   {::id :main}))

(def docker-api-version "v1.41")

(def containers (docker/client {:category    :containers
                                :conn        {:uri "unix:///var/run/docker.sock"}
                                :api-version docker-api-version}))

(def images (docker/client {:category    :images
                            :conn        {:uri "unix:///var/run/docker.sock"}
                            :api-version docker-api-version}))

(def volumes (docker/client {:category    :volumes
                             :conn        {:uri "unix:///var/run/docker.sock"}
                             :api-version docker-api-version}))

(def networks (docker/client {:category    :networks
                              :conn        {:uri "unix:///var/run/docker.sock"}
                              :api-version docker-api-version}))


(defn build-dind-image
  []
  (go
    (let [context-dirpath (-> (clojure.java.io/resource "test/dind.Dockerfile")
                              (clojure.java.io/file)
                              (.getParent))]
      (clojure.java.shell/sh
       "docker" "build"
       "-t" "deathstar-dev-dind"
       "-f" "dind.Dockerfile" context-dirpath
       :dir context-dirpath
       :env {}))))

(defn create-image
  [opts]
  (go
    (let []
      (docker/invoke images
                     {:op     :ImageCreate
                      :params {:fromImage (::image-name opts)}}))))

(defn create-peer-container
  [opts]
  (go))
