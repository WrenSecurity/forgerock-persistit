/**
 * Copyright (C) 2011 Akiban Technologies Inc.
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses.
 */

package com.persistit;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import com.persistit.util.ArgParser;

/**
 * 
 * Meter I/O operations so that background I/O-intensive processes (e.g., the
 * JournalManager's JOURNAL_COPIER thread) can voluntarily throttle I/O
 * consumption.
 * 
 * This class contains methods called by the JournalManager and BufferPool to
 * register various I/O operations performed there. The chargeXX methods modify
 * a various called _ioRate. _ioRate is an indication of how many I/O operations
 * are being invoked by Persistit.
 * 
 * The chargeXX method can also optionally write details for each I/O operation
 * an ancillary I/O log file. The file can be used to
 * 
 * @author peter
 * 
 */
class IOMeter implements IOMeterMXBean {

    final static int URGENT = 10;
    final static int ALMOST_URGENT = 8;
    final static int HALF_URGENT = 5;

    private static final String DUMP_FORMAT = "time=%,12d op=%2s vol=%4s page=%,16d addr=%,16d size=%,8d index=%,7d";

    private final static long NANOS_TO_MILLIS = 1000000l;
    private final static long DEFAULT_TICK_SIZE = 500 * NANOS_TO_MILLIS;
    private final static int DEFAULT_QUIESCENT_IO_THRESHOLD = 100000;
    private final static long DEFAULT_COPY_PAGE_SLEEP_INTERVAL = 10;
    private final static long DEFAULT_WRITE_PAGE_SLEEP_INTERVAL = 100;

    private final static int READ_PAGE_FROM_VOLUME = 1;
    private final static int READ_PAGE_FROM_JOURNAL = 2;
    private final static int COPY_PAGE_TO_VOLUME = 3;
    private final static int WRITE_PAGE_TO_JOURNAL = 4;
    private final static int WRITE_TS_TO_JOURNAL = 5;
    private final static int WRITE_TC_TO_JOURNAL = 6;
    private final static int WRITE_SR_TO_JOURNAL = 7;
    private final static int WRITE_DR_TO_JOURNAL = 8;
    private final static int WRITE_DT_TO_JOURNAL = 9;
    private final static int WRITE_OTHER_TO_JOURNAL = 10;
    private final static int EVICT_PAGE_FROM_POOL = 11;
    private final static int FLUSH_JOURNAL = 12;
    private final static int GET_PAGE = 13;

    private final static int ITEM_COUNT = 14;

    private long _ioRate;

    private long _lastTick;

    private long _tickSize = DEFAULT_TICK_SIZE;

    private long _copyPageSleepInterval = DEFAULT_COPY_PAGE_SLEEP_INTERVAL;

    private long _writePageSleepInterval = DEFAULT_WRITE_PAGE_SLEEP_INTERVAL;

    private long _quiescentIOthreshold = DEFAULT_QUIESCENT_IO_THRESHOLD;

    private AtomicReference<DataOutputStream> _logStream = new AtomicReference<DataOutputStream>();

    private String _logFileName;

    private AtomicLong[] _count = new AtomicLong[ITEM_COUNT];
    private AtomicLong[] _sum = new AtomicLong[ITEM_COUNT];

    IOMeter() {
        for (int index = 0; index < ITEM_COUNT; index++) {
            _count[index] = new AtomicLong();
            _sum[index] = new AtomicLong();
        }
    }

    /**
     * @return the writePageSleepInterval
     */
    @Override
    public synchronized long getWritePageSleepInterval() {
        return _writePageSleepInterval;
    }

    /**
     * @param writePageSleepInterval
     *            the writePageSleepInterval to set
     */
    @Override
    public synchronized void setWritePageSleepInterval(long writePageSleepInterval) {
        _writePageSleepInterval = writePageSleepInterval;
    }

    /**
     * Time interval in milliseconds between page copy operations.
     * 
     * @return the CopySleepInterval
     */
    @Override
    public synchronized long getCopyPageSleepInterval() {
        return _copyPageSleepInterval;
    }

    /**
     * @param copyPageSleepInterval
     *            the copySleepInterval to set
     */
    @Override
    public synchronized void setCopyPageSleepInterval(long copyPageSleepInterval) {
        _copyPageSleepInterval = copyPageSleepInterval;
    }

    /**
     * @return the quiescentIOthreshold
     */
    @Override
    public synchronized long getQuiescentIOthreshold() {
        return _quiescentIOthreshold;
    }

    /**
     * @param quiescentIOthreshold
     *            the quiescentIOthreshold to set
     */
    @Override
    public synchronized void setQuiescentIOthreshold(long quiescentIO) {
        _quiescentIOthreshold = quiescentIO;
    }

    /**
     * @return the ioRate
     */
    @Override
    public synchronized long getIoRate() {
        charge(System.nanoTime(), 0, 0);
        return _ioRate;
    }

    @Override
    public synchronized void setLogFile(final String toFile) throws IOException {
        if (toFile == null || toFile.isEmpty()) {
            final DataOutputStream dos = _logStream.get();
            if (dos != null) {
                _logStream.set(null);
                dos.close();
            }
        } else if (_logStream.get() == null) {
            _logStream.set(new DataOutputStream(new BufferedOutputStream(new FileOutputStream(toFile))));
        }
        _logFileName = toFile;
    }

    @Override
    public String getLogFile() {
        return _logFileName;
    }

    private void charge(final long time, final int size, int item) {
        synchronized (this) {
            final long ticks = (time - _lastTick) / _tickSize;
            if (ticks > 10) {
                _ioRate = 0;
            } else {
                _ioRate >>>= ticks;
            }
            _lastTick = (time / _tickSize) * _tickSize;
            _ioRate += size;
        }
        _count[item].incrementAndGet();
        _sum[item].addAndGet(size);
    }

    private void log(int type, final long time, final Volume volume, long pageAddress, int size, long journalAddress,
            int bufferIndex) {
        final DataOutputStream os = _logStream.get();
        if (os != null) {
            synchronized (os) {
                try {
                    os.write((byte) type);
                    os.writeLong(time);
                    os.writeInt(volume == null ? 0 : volume.hashCode());
                    os.writeLong(pageAddress);
                    os.writeInt(size);
                    os.writeLong(journalAddress);
                    os.writeInt(bufferIndex);
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    private void dump(final DataInputStream is) throws IOException {
        long start = 0;
        final Map<Integer, Integer> volMap = new HashMap<Integer, Integer>();
        int counter = 0;
        while (true) {
            try {
                final int type = is.read();
                if (type == -1) {
                    break;
                }
                final String opName = type >= 0 && type < OPERATIONS.length ? OPERATIONS[type] : "??";
                final long time = is.readLong() / NANOS_TO_MILLIS;
                final int volumeHash = is.readInt();
                final long pageAddress = is.readLong();
                final int size = is.readInt();
                final long journalAddress = is.readLong();
                final int bufferIndex = is.readInt();
                if (start == 0) {
                    start = time;
                }
                Integer volumeId = volMap.get(volumeHash);
                if (volumeId == null) {
                    volumeId = Integer.valueOf(++counter);
                    volMap.put(volumeHash, volumeId);
                }
                System.out.println(String.format(DUMP_FORMAT, time - start, opName, volumeId, pageAddress,
                        journalAddress, size, bufferIndex));

            } catch (EOFException e) {
                break;
                // normal end of processing
            }
        }
    }

    public void chargeCopyPageToVolume(final Volume volume, final long pageAddress, final int size,
            final long journalAddress, final int urgency) {
        final long time = System.nanoTime();
        log(COPY_PAGE_TO_VOLUME, time, volume, pageAddress, size, journalAddress, 0);

        // Determine how long to sleep. This logic attempts to slow down
        // page copy operations when there are a lot of other I/O operations
        // going on concurrently. But when the ioRate falls below the
        // _quiecentIO threshold, or when the need for copying is nearly
        // URGENT, it returns immediately without sleeping.
        //
        long sleep = 0;
        if (urgency < URGENT - 2) {
            synchronized (this) {
                sleep = _ioRate > _quiescentIOthreshold ? _copyPageSleepInterval : 0;
            }
        }
        if (sleep > 0) {
            try {
                Thread.sleep(sleep);
            } catch (InterruptedException e) {
                // Ignore
            }
        }
    }

    public void chargeReadPageFromVolume(final Volume volume, final long pageAddress, final int size, int bufferIndex) {
        final long time = System.nanoTime();
        log(READ_PAGE_FROM_VOLUME, time, volume, pageAddress, size, -1, bufferIndex);
        charge(time, size, READ_PAGE_FROM_VOLUME);
    }

    public void chargeReadPageFromJournal(final Volume volume, final long pageAddress, final int size,
            final long journalAddress, int bufferIndex) {
        final long time = System.nanoTime();
        log(READ_PAGE_FROM_JOURNAL, time, volume, pageAddress, size, journalAddress, bufferIndex);
        charge(time, size, READ_PAGE_FROM_JOURNAL);
    }

    public void chargeWritePageToJournal(final Volume volume, final long pageAddress, final int size,
            final long journalAddress, final int urgency, int bufferIndex) {
        final long time = System.nanoTime();
        log(WRITE_PAGE_TO_JOURNAL, time, volume, pageAddress, size, journalAddress, bufferIndex);
        charge(time, size, WRITE_PAGE_TO_JOURNAL);

        //
        // Following throttles all clients by preventing dirty pages from being
        // added to the journal faster than the copier can clear them.
        //
        if (urgency == URGENT) {
            try {
                Thread.sleep(_writePageSleepInterval);
            } catch (InterruptedException ie) {

            }
        }
    }

    public void chargeWriteTStoJournal(final int size, final long journalAddress) {
        final long time = System.nanoTime();
        log(WRITE_TS_TO_JOURNAL, time, null, -1, size, journalAddress, -1);
        charge(time, size, WRITE_TS_TO_JOURNAL);
    }

    public void chargeWriteTCtoJournal(final int size, final long journalAddress) {
        final long time = System.nanoTime();
        log(WRITE_TC_TO_JOURNAL, time, null, -1, size, journalAddress, -1);
        charge(time, size, WRITE_TC_TO_JOURNAL);
    }

    public void chargeWriteSRtoJournal(final int size, final long journalAddress) {
        final long time = System.nanoTime();
        log(WRITE_SR_TO_JOURNAL, time, null, -1, size, journalAddress, -1);
        charge(time, size, WRITE_SR_TO_JOURNAL);
    }

    public void chargeWriteDRtoJournal(final int size, final long journalAddress) {
        final long time = System.nanoTime();
        log(WRITE_DR_TO_JOURNAL, time, null, -1, size, journalAddress, -1);
        charge(time, size, WRITE_DR_TO_JOURNAL);
    }

    public void chargeWriteDTtoJournal(final int size, final long journalAddress) {
        final long time = System.nanoTime();
        log(WRITE_DT_TO_JOURNAL, time, null, -1, size, journalAddress, -1);
        charge(time, size, WRITE_DT_TO_JOURNAL);
    }

    public void chargeWriteOtherToJournal(final int size, final long journalAddress) {
        final long time = System.nanoTime();
        log(WRITE_OTHER_TO_JOURNAL, time, null, -1, size, journalAddress, -1);
        charge(time, size, WRITE_OTHER_TO_JOURNAL);
    }

    public void chargeEvictPageFromPool(final Volume volume, final long pageAddress, final int size, int bufferIndex) {
        final long time = System.nanoTime();
        log(EVICT_PAGE_FROM_POOL, time, volume, pageAddress, size, 0, bufferIndex);
    }

    public void chargeFlushJournal(final int size, final long journalAddress) {
        final long time = System.nanoTime();
        log(FLUSH_JOURNAL, time, null, -1, size, journalAddress, -1);
        charge(time, size, FLUSH_JOURNAL);
    }

    public void chargeGetPage(final Volume volume, final long pageAddress, final int size, int bufferIndex) {
        final long time = System.nanoTime();
        log(GET_PAGE, time, volume, pageAddress, size, 0, bufferIndex);
    }

    @Override
    public long getCount(final String opName) {
        return getCount(op(opName));
    }

    public long getCount(final int op) {
        if (op > 0 && op < ITEM_COUNT) {
            return _count[op].get();
        } else {
            return -1;
        }
    }

    @Override
    public long getSum(final String opName) {
        return getSum(op(opName));
    }

    public long getSum(final int op) {
        if (op > 0 && op < ITEM_COUNT) {
            return _sum[op].get();
        } else {
            return -1;
        }
    }

    public int op(final String opName) {
        for (int index = 1; index < ITEM_COUNT; index++) {
            if (OPERATIONS[index].equalsIgnoreCase(opName)) {
                return index;
            }
        }
        return -1;
    }

    /**
     * Dump an IOMeter log file to System.out. For diagnostic purposes only.
     * 
     * @param args
     *            Specify one argument in the form file=<pathname>
     * @throws Exception
     */
    public static void main(final String[] args) throws Exception {
        final ArgParser ap = new ArgParser("com.persistit.IOMeter", args, new String[] { "file||log file name" });
        final String fileName = ap.getStringValue("file");
        if (fileName == null) {
            ap.usage();
        } else {
            IOMeter ioMeter = new IOMeter();
            final DataInputStream is = new DataInputStream(new BufferedInputStream(new FileInputStream(fileName)));
            ioMeter.dump(is);
            is.close();
        }
    }
}
