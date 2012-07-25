/**
 * Copyright © 2011-2012 Akiban Technologies, Inc.  All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, version 3 (only) of the
 * License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * This program may also be available under different license terms. For more
 * information, see www.akiban.com or contact licensing@akiban.com.
 */

package com.persistit;

import org.junit.Test;

import com.persistit.Exchange;
import com.persistit.Transaction;

/**
 * 
 * From akiban-server 0.81:
 * 
 * ERROR BTree structure error Volume akiban_txn(/var/lib/akiban/akiban_txn.v0)
 * 
 * level=0 page=35
 * key=<{83,(char)82,(long)20110115200202,(long)20111110094933,(long
 * )1688310,(long)1688310 ,(long)24091208}-> is before left edge
 * 
 * Exchange(Volume=/var/lib/akiban/akiban_txn.v0,Tree=_txn_100000004,,Key=<{83,(
 * char
 * )82,(long)20110115200202,(long)20111110094933,(long)1688310,(long)1688310,
 * (long)24091208}->)
 * 
 * 0: Buffer=<Page 58 in volume akiban_txn(/var/lib/akiban/akiban_txn.v0) at
 * index 88,860 timestamp=1,935,552,865 status=vdwr1 <Network-Worker-Thread-1>
 * type=Data>, keyGeneration=115650,
 * bufferGeneration=559,foundAt=<32:fixup:depth=10:ebc=0:db=37:tail=6496>>
 * 
 * 1: Buffer=<Page 63 in volume akiban_txn(/var/lib/akiban/akiban_txn.v0) at
 * index 88,861 timestamp=1,935,552,865 status=vd
 * type=Index1>,keyGeneration=115650,
 * bufferGeneration=16,foundAt=<36:fixup:depth=9:ebc=0:db=37:tail=16188>>
 */

public class Bug889850Test extends PersistitUnitTestCase {

    @Test
    public void testDeleteKeysFromTransaction() throws Exception {
        final Exchange ex = _persistit.getExchange("persistit", "bug889850", true);
        ex.getValue().put(RED_FOX);
        final Transaction txn = _persistit.getTransaction();
        txn.begin();
        for (int i = 5000; --i >= 0;) {
            ex.to(i);
            ex.store();
        }
        txn.commit();
        txn.end();
        txn.begin();
        for (int i = 0; i < 5000; i++) {
            ex.to(i);
            ex.remove();
        }
        for (int i = 5000; --i >= 0;) {
            ex.to(i);
            ex.remove();
        }
        txn.commit();
        txn.end();
    }

}
