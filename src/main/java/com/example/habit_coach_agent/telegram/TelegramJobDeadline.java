package com.example.habit_coach_agent.telegram;

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

final class TelegramJobDeadline {
    private TelegramJobDeadline() { }
    static <T> T run(Callable<T> work,long seconds) throws Exception {
        FutureTask<T> task=new FutureTask<>(work);
        Thread.startVirtualThread(task);
        try { return task.get(seconds,TimeUnit.SECONDS); }
        finally { if(!task.isDone()) task.cancel(true); }
    }
}
