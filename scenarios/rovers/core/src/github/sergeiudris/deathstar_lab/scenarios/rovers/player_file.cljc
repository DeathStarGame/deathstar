(ns github.sergeiudris.deathstar-lab.scenarios.rovers.player-file
  (:require
   [clojure.core.async :as a :refer [chan go go-loop <! >!  take! put! offer! poll! alt! alts! close!
                                     pub sub unsub mult tap untap mix admix unmix pipe
                                     timeout to-chan  sliding-buffer dropping-buffer
                                     pipeline pipeline-async]]
   [clojure.set :refer [subset?]]

   [github.sergeiudris.deathstar-lab.scenarios.rovers.game-api :as game]))


(comment

  (game/move 3 4)

  ;;
  )