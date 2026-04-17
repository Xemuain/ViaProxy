/*
 * This file is part of ViaProxy - https://github.com/RaphiMC/ViaProxy
 * Copyright (C) 2021-2026 RK_01/RaphiMC and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.raphimc.viaproxy.saves.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.lenni0451.reflect.Classes;
import net.raphimc.viaproxy.ViaProxy;
import net.raphimc.viaproxy.plugins.ViaProxyPlugin;
import net.raphimc.viaproxy.saves.AbstractSave;
import net.raphimc.viaproxy.saves.impl.accounts.Account;
import net.raphimc.viaproxy.saves.impl.accounts.OfflineAccount;
import net.raphimc.viaproxy.util.logging.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AccountsSave extends AbstractSave {

    public sealed interface Entry permits KnownEntry, UnknownEntry { }
    public record KnownEntry(Account account) implements Entry { }
    public record UnknownEntry(JsonObject jsonObject) implements Entry { }

    private final List<Entry> entries = new ArrayList<>();

    public AccountsSave() {
        super("accountsV4");
    }

    @Override
    public void load(JsonElement jsonElement) throws Exception {
        this.entries.clear();
        this.entries.addAll(loadFromJsonArray(jsonElement.getAsJsonArray()));
    }

    @Override
    public JsonElement save() {
        final JsonArray array = new JsonArray();
        for (Entry entry : this.entries) {
            if (entry instanceof KnownEntry knownEntry) {
                Account account = knownEntry.account();
                JsonObject json = account.toJson();
                json.addProperty("accountType", account.getClass().getName());
                array.add(json);
            } else if (entry instanceof UnknownEntry unknownEntry) {
                array.add(unknownEntry.jsonObject());
            }
        }
        return array;
    }

    public Account addAccount(final String username) {
        return addAccount(new OfflineAccount(username));
    }

    public Account addAccount(final Account account) {
        this.entries.add(new KnownEntry(account));
        return account;
    }

    public Account addAccount(final int index, final Account account) {
        this.entries.add(index, new KnownEntry(account));
        return account;
    }

    public void removeAccount(final Account account) {
        this.entries.removeIf(entry -> entry instanceof KnownEntry knownEntry && knownEntry.account() == account);
    }

    public List<Account> getAccounts() {
        final List<Account> accounts = new ArrayList<>();
        for (Entry entry : this.entries) {
            if (entry instanceof KnownEntry knownEntry)
                accounts.add(knownEntry.account);
        }
        return accounts;
    }

    public Entry addEntry(Entry entry) {
        this.entries.add(entry);
        return entry;
    }

    public List<Entry> getEntries() {
        return Collections.unmodifiableList(this.entries);
    }

    public List<Entry> loadFromJsonArray(JsonArray array) {
        final List<Entry> loaded = new ArrayList<>();

        final List<ClassLoader> classLoaders = new ArrayList<>();
        classLoaders.add(ViaProxy.class.getClassLoader());
        classLoaders.addAll(ViaProxy.getPluginManager().getPlugins().stream().map(ViaProxyPlugin::getClassLoader).toList());

        for (JsonElement element : array) {
            try {
                final JsonObject jsonObject = element.getAsJsonObject();
                final String type = jsonObject.get("accountType").getAsString();

                Class<?> clazz;
                try {
                    clazz = Classes.find(type, true, classLoaders);
                } catch (Exception exception) {
                    if (exception instanceof ClassNotFoundException) {
                        loaded.add(new UnknownEntry(jsonObject));
                        continue;
                    }

                    throw exception;
                }

                final Account account = (Account) clazz.getConstructor(JsonObject.class).newInstance(jsonObject);
                loaded.add(new KnownEntry(account));
            } catch (Throwable e) {
                Logger.LOGGER.error("Failed to load an account", e);
            }
        }

        return loaded;
    }
}
