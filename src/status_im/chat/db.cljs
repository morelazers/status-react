(ns status-im.chat.db
  (:require [clojure.set :as clojure.set]
            [clojure.string :as string]
            [status-im.chat.commands.core :as commands]
            [status-im.chat.commands.input :as commands.input]
            [status-im.utils.config :as utils.config]
            [status-im.utils.gfycat.core :as gfycat]))

(defn chat-name
  [{:keys [group-chat
           chat-id
           public?
           name]}
   {contact-name :name}]
  (cond
    public?    (str "#" name)
    group-chat name
    :else      (or contact-name
                   (gfycat/generate-gfy chat-id))))

(defn active-chats
  [contacts chats {:keys [dev-mode?]}]
  (reduce (fn [acc [chat-id {:keys [group-chat public? is-active] :as chat}]]
            (if (and is-active
                     ;; not a group chat
                     (or (not (and group-chat (not public?)))
                         ;; if it's a group chat
                         utils.config/group-chats-enabled?))
              (assoc acc chat-id (if-let [contact (get contacts chat-id)]
                                   (-> chat
                                       (assoc :name (:name contact))
                                       (assoc :random-name (gfycat/generate-gfy (:public-key contact)))
                                       (update :tags clojure.set/union (:tags contact)))
                                   chat))
              acc))
          {}
          chats))

(def map->sorted-seq
  (comp (partial map second) (partial sort-by first)))

(defn available-commands
  [commands {:keys [input-text]}]
  (->> commands
       map->sorted-seq
       (filter (fn [{:keys [type]}]
                 (when (commands.input/starts-as-command? input-text)
                   (string/includes? (commands/command-name type) input-text))))))
