package com.lunar.concurrent;

import java.util.concurrent.locks.LockSupport;

import sun.misc.Signal;

public class SigIntBarrier {
    private final Thread thread;
    private volatile boolean running = true;

    public SigIntBarrier()
    {
        thread = Thread.currentThread();

        Signal.handle(
            new Signal("INT"),
            (signal) ->
            {
                running = false;
                LockSupport.unpark(thread);
            });
    }
    public void await()
    {
        while (running)
        {
            LockSupport.park();
        }
    }
}
