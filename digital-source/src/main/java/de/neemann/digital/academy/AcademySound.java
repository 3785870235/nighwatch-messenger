/*
 * Copyright (c) 2026 Midnight Logic Academy contributors.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.academy;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import java.io.File;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Layered original local WAV cues; sound never produces simulation state. */
final class AcademySound {
    private static final Map<Clip, Object> ACTIVE = new HashMap<>();
    private static final Map<Object, Long> AMBIENCE = new IdentityHashMap<>();
    private static final Map<String, Long> LAST = new HashMap<>();
    private static final ExecutorService AUDIO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "academy-sound");
        t.setDaemon(true);
        return t;
    });
    private static long generation;
    private static long ownerCounter;
    private static boolean unavailable;
    private static int pending;

    private AcademySound() {
    }

    static void cue(String kind) {
        cue(kind, null);
    }

    static void cue(String kind, Object owner) {
        synchronized (ACTIVE) {
            long now = System.nanoTime();
            if (!AcademyAtmosphere.sound() || unavailable || pending >= 8
                    || now - LAST.getOrDefault(kind, 0L) < 110000000L)
                return;
            LAST.put(kind, now);
            enqueue(kind, owner, false, generation, owner == null ? 0 : AMBIENCE.getOrDefault(owner, -1L));
        }
    }

    static void ambience(Object owner) {
        synchronized (ACTIVE) {
            if (!AcademyAtmosphere.sound() || unavailable || AMBIENCE.containsKey(owner))
                return;
            long stamp = ++ownerCounter;
            AMBIENCE.put(owner, stamp);
            enqueue("wind", owner, true, generation, stamp);
            enqueue("embers", owner, true, generation, stamp);
        }
    }

    private static boolean valid(Object owner, long ticket, long stamp) {
        return AcademyAtmosphere.sound() && ticket == generation
                && (owner == null || AMBIENCE.getOrDefault(owner, -2L) == stamp);
    }

    private static void enqueue(String name, Object owner, boolean loop, long ticket, long stamp) {
        pending++;
        AUDIO.submit(() -> {
            Clip clip = null;
            try {
                synchronized (ACTIVE) {
                    if (!valid(owner, ticket, stamp) || ACTIVE.size() >= 12)
                        return;
                }
                File root = new File(System.getProperty("academy.root", "."));
                File source = new File(root, "assets/game/v2/audio/" + name + ".wav");
                if (!source.isFile())
                    return;
                clip = AudioSystem.getClip();
                try (AudioInputStream input = AudioSystem.getAudioInputStream(source)) {
                    clip.open(input);
                }
                final Clip playing = clip;
                playing.addLineListener(e -> {
                    if (e.getType() == LineEvent.Type.STOP) {
                        synchronized (ACTIVE) {
                            ACTIVE.remove(playing);
                        }
                        playing.close();
                    }
                });
                synchronized (ACTIVE) {
                    if (!valid(owner, ticket, stamp)) {
                        clip.close();
                        return;
                    }
                    ACTIVE.put(clip, owner);
                    if (loop)
                        clip.loop(Clip.LOOP_CONTINUOUSLY);
                    else
                        clip.start();
                }
            } catch (Exception e) {
                if (clip != null)
                    clip.close();
                synchronized (ACTIVE) {
                    unavailable = true;
                }
                System.err.println("[academy] sound unavailable: " + e.getClass().getSimpleName());
            } finally {
                synchronized (ACTIVE) {
                    pending--;
                }
            }
        });
    }

    static void stop(Object owner) {
        List<Clip> closing = new ArrayList<>();
        synchronized (ACTIVE) {
            AMBIENCE.remove(owner);
            for (Map.Entry<Clip, Object> e : new HashMap<>(ACTIVE).entrySet()) {
                if (e.getValue() == owner) {
                    ACTIVE.remove(e.getKey());
                    closing.add(e.getKey());
                }
            }
        }
        for (Clip clip : closing) {
            clip.stop();
            clip.close();
        }
    }

    static void stopAll() {
        List<Clip> closing;
        synchronized (ACTIVE) {
            generation++;
            AMBIENCE.clear();
            LAST.clear();
            closing = new ArrayList<>(ACTIVE.keySet());
            ACTIVE.clear();
        }
        for (Clip clip : closing) {
            clip.stop();
            clip.close();
        }
    }

    static int activeClips() {
        synchronized (ACTIVE) {
            return ACTIVE.size();
        }
    }

    static int pendingClips() {
        synchronized (ACTIVE) {
            return pending;
        }
    }
}
