(ns status-im.ui.screens.home.subs
  (:require [re-frame.core :as re-frame]))

(re-frame/reg-sub
 :home-items
 :<- [:chats/active-chats]
 :<- [:browser/browsers]
 (fn [[chats browsers]]
   (sort-by #(-> % second :timestamp)
            >
            (merge chats browsers))))
