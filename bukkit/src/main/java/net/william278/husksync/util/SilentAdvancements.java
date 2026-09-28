/*
 * This file is part of HuskSync, licensed under the Apache License 2.0.
 *
 *  Copyright (c) William278 <will27528@gmail.com>
 *  Copyright (c) contributors
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package net.william278.husksync.util;

import net.william278.husksync.HuskSync;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.logging.Level;

// XMine start - выдача достижений без тостов
/**
 * Выдаёт достижения игроку так, чтобы клиент не показал по ним всплывающие уведомления (тосты).
 * <p>
 * Тост клиент рисует, когда пришедший пакет обновления достижений выставляет флаг
 * {@code showAdvancements}, а достижение в нём выполнено. Ванильная команда {@code /advancement}
 * поэтому устроена так: сначала отправляет накопленное с {@code true}, затем выдаёт, затем
 * отправляет свежевыданное с {@code false}. Здесь то же самое, чтобы накат достижений из
 * снапшота при входе на ноду не выглядел как новое получение.
 * <p>
 * В Bukkit API для этого нет ничего, поэтому вызов {@code ServerPlayer#getAdvancements()
 * .flushDirty(ServerPlayer, boolean)} идёт рефлексией: модуль собирается только против paper-api.
 * Paper 26.1 не обфусцирован, имена методов стабильны. Если они пропали, рефлексия отключается
 * (один раз, с предупреждением) и достижения выдаются как обычно, с тостами.
 */
public final class SilentAdvancements {

    private static volatile boolean unavailable = false;
    private static Method getHandle;
    private static Method getAdvancements;
    private static Method flushDirty;

    private SilentAdvancements() {
    }

    /**
     * Выполняет {@code award} (выдачу достижений игроку) без тостов; если рефлексия недоступна —
     * просто выполняет его. Вызывать в потоке игрока.
     */
    public static void run(@NotNull HuskSync plugin, @NotNull Player player, @NotNull Runnable award) {
        if (unavailable) {
            award.run();
            return;
        }
        final Object serverPlayer;
        final Object advancements;
        try {
            resolve(player);
            serverPlayer = getHandle.invoke(player);
            advancements = getAdvancements.invoke(serverPlayer);
            // Накопленное до нас уходит как обычно: иначе оно тоже потеряло бы тосты
            flushDirty.invoke(advancements, serverPlayer, true);
        } catch (ReflectiveOperationException | RuntimeException e) {
            disable(plugin, e);
            award.run();
            return;
        }

        try {
            award.run();
        } finally {
            try {
                flushDirty.invoke(advancements, serverPlayer, false);
            } catch (ReflectiveOperationException | RuntimeException e) {
                disable(plugin, e);
            }
        }
    }

    private static synchronized void resolve(@NotNull Player player) throws ReflectiveOperationException {
        if (flushDirty != null) {
            return;
        }
        getHandle = player.getClass().getMethod("getHandle");
        getAdvancements = findMethod(getHandle.getReturnType(), "getAdvancements", 0);
        flushDirty = findMethod(getAdvancements.getReturnType(), "flushDirty", 2);
    }

    private static Method findMethod(@NotNull Class<?> type, @NotNull String name, int parameters)
            throws NoSuchMethodException {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == parameters) {
                return method;
            }
        }
        throw new NoSuchMethodException(type.getName() + "#" + name);
    }

    private static void disable(@NotNull HuskSync plugin, @NotNull Exception cause) {
        if (!unavailable) {
            unavailable = true;
            plugin.log(Level.WARNING, "Could not suppress advancement toasts on sync (synchronization."
                    + "silent_advancements); players will see them again: " + cause, cause);
        }
    }
}
// XMine end - выдача достижений без тостов
