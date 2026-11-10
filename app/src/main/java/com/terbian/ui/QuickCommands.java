/*
 * Copyright 2026 deepcodecreate
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.terbian.ui;

import android.content.Context;

import com.terbian.prefs.AppSettings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class QuickCommands {
    private static final String KEY = "quick_commands";

    private QuickCommands() {
    }

    public static final class Item {
        public final String name;
        public final String command;

        public Item(String name, String command) {
            this.name = name;
            this.command = command;
        }
    }

    public static List<Item> load(Context context) {
        String raw = AppSettings.prefs(context).getString(KEY, null);
        List<Item> items = new ArrayList<>();
        if (raw == null) {
            items.add(new Item("Update packages", "apt update && apt upgrade -y"));
            items.add(new Item("Disk usage", "df -h"));
            items.add(new Item("Memory", "free -h"));
            items.add(new Item("System info", "uname -a"));
            items.add(new Item("Start desktop", "terbian-gui start"));
            return items;
        }
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                items.add(new Item(o.optString("name"), o.optString("command")));
            }
        } catch (Exception ignored) {
        }
        return items;
    }

    public static void save(Context context, List<Item> items) {
        JSONArray array = new JSONArray();
        try {
            for (Item item : items) {
                JSONObject o = new JSONObject();
                o.put("name", item.name);
                o.put("command", item.command);
                array.put(o);
            }
        } catch (Exception ignored) {
        }
        AppSettings.prefs(context).edit().putString(KEY, array.toString()).apply();
    }
}
