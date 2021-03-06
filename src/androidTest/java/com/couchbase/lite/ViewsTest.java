/**
 * Original iOS version by  Jens Alfke
 * Ported to Android by Marty Schoch
 *
 * Copyright (c) 2012 Couchbase, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package com.couchbase.lite;

import com.couchbase.lite.View.TDViewCollation;
import com.couchbase.lite.internal.RevisionInternal;
import com.couchbase.lite.support.LazyJsonArray;
import com.couchbase.lite.util.Log;

import junit.framework.Assert;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;


public class ViewsTest extends LiteTestCase {

    public static final String TAG = "Views";

    public void testQueryDefaultIndexUpdateMode() {

        View view = database.getView("aview");
        Query query = view.createQuery();
        assertEquals(Query.IndexUpdateMode.BEFORE, query.getIndexUpdateMode());

    }

    public void testViewCreation() {

        Assert.assertNull(database.getExistingView("aview"));

        View view = database.getView("aview");
        Assert.assertNotNull(view);
        Assert.assertEquals(database, view.getDatabase());
        Assert.assertEquals("aview", view.getName());
        Assert.assertNull(view.getMap());
        Assert.assertEquals(view, database.getExistingView("aview"));

        boolean changed = view.setMapReduce(new Mapper() {

            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                //no-op
            }
        }, null, "1");

        Assert.assertTrue(changed);
        Assert.assertEquals(1, database.getAllViews().size());
        Assert.assertEquals(view, database.getAllViews().get(0));

        changed = view.setMapReduce(new Mapper() {

            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                //no-op
            }
        }, null, "1");

        Assert.assertFalse(changed);

        changed = view.setMapReduce(new Mapper() {

            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                //no-op
            }
        }, null, "2");

        Assert.assertTrue(changed);
    }

    //https://github.com/couchbase/couchbase-lite-java-core/issues/219
    public void testDeleteView() {
        List<View> views = database.getAllViews();
        for (View view : views) {
            database.deleteViewNamed(view.getName());
        }

        Assert.assertEquals(0, database.getAllViews().size());
        Assert.assertEquals(null, database.getExistingView("viewToDelete"));


        View view = database.getView("viewToDelete");
        Assert.assertNotNull(view);
        Assert.assertEquals(database, view.getDatabase());
        Assert.assertEquals("viewToDelete", view.getName());
        Assert.assertNull(view.getMap());
        Assert.assertEquals(view, database.getExistingView("viewToDelete"));

        Assert.assertEquals(0, database.getAllViews().size());
        boolean changed = view.setMapReduce(new Mapper() {

            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                //no-op
            }
        }, null, "1");

        Assert.assertTrue(changed);
        Assert.assertEquals(1, database.getAllViews().size());
        Assert.assertEquals(view, database.getAllViews().get(0));

        Status status = database.deleteViewNamed("viewToDelete");
        Assert.assertEquals(Status.OK, status.getCode());
        Assert.assertEquals(0, database.getAllViews().size());

        View nullView = database.getExistingView("viewToDelete");
        Assert.assertNull("cached View is not deleted", nullView);

        status = database.deleteViewNamed("viewToDelete");
        Assert.assertEquals(Status.NOT_FOUND, status.getCode());
    }

    private RevisionInternal putDoc(Database db, Map<String,Object> props) throws CouchbaseLiteException {
        RevisionInternal rev = new RevisionInternal(props);
        Status status = new Status();
        rev = db.putRevision(rev, null, false, status);
        Assert.assertTrue(status.isSuccessful());
        return rev;
    }

    private void putDocViaUntitledDoc(Database db, Map<String, Object> props) throws CouchbaseLiteException {
        Document document = db.createDocument();
        document.putProperties(props);
    }

    public List<RevisionInternal> putDocs(Database db) throws CouchbaseLiteException {
        List<RevisionInternal> result = new ArrayList<RevisionInternal>();

        Map<String,Object> dict2 = new HashMap<String,Object>();
        dict2.put("_id", "22222");
        dict2.put("key", "two");
        result.add(putDoc(db, dict2));

        Map<String,Object> dict4 = new HashMap<String,Object>();
        dict4.put("_id", "44444");
        dict4.put("key", "four");
        result.add(putDoc(db, dict4));

        Map<String,Object> dict1 = new HashMap<String,Object>();
        dict1.put("_id", "11111");
        dict1.put("key", "one");
        result.add(putDoc(db, dict1));

        Map<String,Object> dict3 = new HashMap<String,Object>();
        dict3.put("_id", "33333");
        dict3.put("key", "three");
        result.add(putDoc(db, dict3));

        Map<String,Object> dict5 = new HashMap<String,Object>();
        dict5.put("_id", "55555");
        dict5.put("key", "five");
        result.add(putDoc(db, dict5));

        return result;
    }

    // http://wiki.apache.org/couchdb/Introduction_to_CouchDB_views#Linked_documents
    public List<RevisionInternal> putLinkedDocs(Database db) throws CouchbaseLiteException {
        List<RevisionInternal> result = new ArrayList<RevisionInternal>();

        Map<String,Object> dict1 = new HashMap<String,Object>();
        dict1.put("_id", "11111");
        result.add(putDoc(db, dict1));

        Map<String,Object> dict2 = new HashMap<String,Object>();
        dict2.put("_id", "22222");
        dict2.put("value", "hello");
        dict2.put("ancestors", new String[]{"11111"});
        result.add(putDoc(db, dict2));

        Map<String,Object> dict3 = new HashMap<String,Object>();
        dict3.put("_id", "33333");
        dict3.put("value", "world");
        dict3.put("ancestors", new String[] { "22222", "11111" });
        result.add(putDoc(db, dict3));

        return result;
    }

    
    public void putNDocs(Database db, int n) throws CouchbaseLiteException {
        for(int i=0; i< n; i++) {
            Map<String,Object> doc = new HashMap<String,Object>();
            doc.put("_id", String.format("%d", i));
            List<String> key = new ArrayList<String>();
            for(int j=0; j< 256; j++) {
                key.add("key");
            }
            key.add(String.format("key-%d", i));
            doc.put("key", key);
            putDocViaUntitledDoc(db, doc);
        }
    }

    public static View createView(Database db) {
        View view = db.getView("aview");
        view.setMapReduce(new Mapper() {

            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                Assert.assertNotNull(document.get("_id"));
                Assert.assertNotNull(document.get("_rev"));
                if (document.get("key") != null) {
                    emitter.emit(document.get("key"), null);
                }
            }
        }, null, "1");
        return view;
    }

    public void testViewIndex() throws CouchbaseLiteException {

        int numTimesMapFunctionInvoked = 0;

        Map<String,Object> dict1 = new HashMap<String,Object>();
        dict1.put("key", "one");
        Map<String,Object> dict2 = new HashMap<String,Object>();
        dict2.put("key", "two");
        Map<String,Object> dict3 = new HashMap<String,Object>();
        dict3.put("key", "three");
        Map<String,Object> dictX = new HashMap<String,Object>();
        dictX.put("clef", "quatre");

        RevisionInternal rev1 = putDoc(database, dict1);
        RevisionInternal rev2 = putDoc(database, dict2);
        RevisionInternal rev3 = putDoc(database, dict3);
        putDoc(database, dictX);

        class InstrumentedMapBlock implements Mapper {

            int numTimesInvoked = 0;

            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                numTimesInvoked += 1;
                Assert.assertNotNull(document.get("_id"));
                Assert.assertNotNull(document.get("_rev"));
                if (document.get("key") != null) {
                    emitter.emit(document.get("key"), null);
                }
            }

            public int getNumTimesInvoked() {
                return numTimesInvoked;
            }

        }
        View view = database.getView("aview");
        InstrumentedMapBlock mapBlock = new InstrumentedMapBlock();
        view.setMap(mapBlock, "1");

        Assert.assertEquals(1, view.getViewId());
        Assert.assertTrue(view.isStale());

        view.updateIndex();

        List<Map<String,Object>> dumpResult = view.dump();
        Log.v(TAG, "View dump: " + dumpResult);
        Assert.assertEquals(3, dumpResult.size());
        Assert.assertEquals("\"one\"", dumpResult.get(0).get("key"));
        Assert.assertEquals(1, dumpResult.get(0).get("seq"));
        Assert.assertEquals("\"two\"", dumpResult.get(2).get("key"));
        Assert.assertEquals(2, dumpResult.get(2).get("seq"));
        Assert.assertEquals("\"three\"", dumpResult.get(1).get("key"));
        Assert.assertEquals(3, dumpResult.get(1).get("seq"));

        //no-op reindex
        Assert.assertFalse(view.isStale());

        view.updateIndex();

        // Now add a doc and update a doc:
        RevisionInternal threeUpdated = new RevisionInternal(rev3.getDocId(), rev3.getRevId(), false);
        numTimesMapFunctionInvoked = mapBlock.getNumTimesInvoked();

        Map<String,Object> newdict3 = new HashMap<String,Object>();
        newdict3.put("key", "3hree");
        threeUpdated.setProperties(newdict3);
        Status status = new Status();
        rev3 = database.putRevision(threeUpdated, rev3.getRevId(), false, status);
        Assert.assertTrue(status.isSuccessful());

        // Reindex again:
        Assert.assertTrue(view.isStale());
        view.updateIndex();

        // Make sure the map function was only invoked one more time (for the document that was added)
        Assert.assertEquals(mapBlock.getNumTimesInvoked(), numTimesMapFunctionInvoked + 1);

        Map<String,Object> dict4 = new HashMap<String,Object>();
        dict4.put("key", "four");
        RevisionInternal rev4 = putDoc(database, dict4);

        RevisionInternal twoDeleted = new RevisionInternal(rev2.getDocId(), rev2.getRevId(), true);
        database.putRevision(twoDeleted, rev2.getRevId(), false, status);
        Assert.assertTrue(status.isSuccessful());

        // Reindex again:
        Assert.assertTrue(view.isStale());
        view.updateIndex();

        dumpResult = view.dump();
        Log.v(TAG, "View dump: " + dumpResult);
        Assert.assertEquals(3, dumpResult.size());
        Assert.assertEquals("\"one\"", dumpResult.get(2).get("key"));
        Assert.assertEquals(1, dumpResult.get(2).get("seq"));
        Assert.assertEquals("\"3hree\"", dumpResult.get(0).get("key"));
        Assert.assertEquals(5, dumpResult.get(0).get("seq"));
        Assert.assertEquals("\"four\"", dumpResult.get(1).get("key"));
        Assert.assertEquals(6, dumpResult.get(1).get("seq"));

        // Now do a real query:
        List<QueryRow> rows = view.queryWithOptions(null);
        Assert.assertEquals(3, rows.size());
        Assert.assertEquals("one", rows.get(2).getKey());
        Assert.assertEquals(rev1.getDocId(), rows.get(2).getDocumentId());
        Assert.assertEquals("3hree", rows.get(0).getKey());
        Assert.assertEquals(rev3.getDocId(), rows.get(0).getDocumentId());
        Assert.assertEquals("four", rows.get(1).getKey());
        Assert.assertEquals(rev4.getDocId(), rows.get(1).getDocumentId());

        view.deleteIndex();
    }

    public void testViewIndexSkipsDesignDocs() throws CouchbaseLiteException {
        View view = createView(database);

        Map<String, Object> designDoc = new HashMap<String, Object>();
        designDoc.put("_id", "_design/test");
        designDoc.put("key", "value");
        putDoc(database, designDoc);

        view.updateIndex();
        List<QueryRow> rows = view.queryWithOptions(null);
        assertEquals(0, rows.size());
    }

    /**
     * https://github.com/couchbase/couchbase-lite-java-core/issues/214
     */
    public void testViewIndexSkipsConflictingDesignDocs() throws CouchbaseLiteException {
        View view = createView(database);

        Map<String, Object> designDoc = new HashMap<String, Object>();
        designDoc.put("_id", "_design/test");
        designDoc.put("key", "value");
        RevisionInternal rev1 = putDoc(database, designDoc);

        designDoc.put("_rev", rev1.getRevId());
        designDoc.put("key", "value2a");
        RevisionInternal rev2a = new RevisionInternal(designDoc);
        database.putRevision(rev2a, rev1.getRevId(), true);
        designDoc.put("key", "value2b");
        RevisionInternal rev2b = new RevisionInternal(designDoc);
        database.putRevision(rev2b, rev1.getRevId(), true);

        view.updateIndex();
        List<QueryRow> rows = view.queryWithOptions(null);
        assertEquals(0, rows.size());
    }

    public void testViewQuery() throws CouchbaseLiteException {

        putDocs(database);
        View view = createView(database);

        view.updateIndex();

        // Query all rows:
        QueryOptions options = new QueryOptions();
        List<QueryRow> rows = view.queryWithOptions(options);

        List<Object> expectedRows = new ArrayList<Object>();

        Map<String,Object> dict5 = new HashMap<String,Object>();
        dict5.put("id", "55555");
        dict5.put("key", "five");
        expectedRows.add(dict5);

        Map<String,Object> dict4 = new HashMap<String,Object>();
        dict4.put("id", "44444");
        dict4.put("key", "four");
        expectedRows.add(dict4);

        Map<String,Object> dict1 = new HashMap<String,Object>();
        dict1.put("id", "11111");
        dict1.put("key", "one");
        expectedRows.add(dict1);

        Map<String,Object> dict3 = new HashMap<String,Object>();
        dict3.put("id", "33333");
        dict3.put("key", "three");
        expectedRows.add(dict3);

        Map<String,Object> dict2 = new HashMap<String,Object>();
        dict2.put("id", "22222");
        dict2.put("key", "two");
        expectedRows.add(dict2);

        Assert.assertEquals(5, rows.size());
        Assert.assertEquals(dict5.get("key"), rows.get(0).getKey());
        Assert.assertEquals(dict5.get("value"), rows.get(0).getValue());
        Assert.assertEquals(dict4.get("key"), rows.get(1).getKey());
        Assert.assertEquals(dict4.get("value"), rows.get(1).getValue());
        Assert.assertEquals(dict1.get("key"), rows.get(2).getKey());
        Assert.assertEquals(dict1.get("value"), rows.get(2).getValue());
        Assert.assertEquals(dict3.get("key"), rows.get(3).getKey());
        Assert.assertEquals(dict3.get("value"), rows.get(3).getValue());
        Assert.assertEquals(dict2.get("key"), rows.get(4).getKey());
        Assert.assertEquals(dict2.get("value"), rows.get(4).getValue());

        // Start/end key query:
        options = new QueryOptions();
        options.setStartKey("a");
        options.setEndKey("one");

        rows = view.queryWithOptions(options);

        expectedRows = new ArrayList<Object>();
        expectedRows.add(dict5);
        expectedRows.add(dict4);
        expectedRows.add(dict1);

        Assert.assertEquals(3, rows.size());
        Assert.assertEquals(dict5.get("key"), rows.get(0).getKey());
        Assert.assertEquals(dict5.get("value"), rows.get(0).getValue());
        Assert.assertEquals(dict4.get("key"), rows.get(1).getKey());
        Assert.assertEquals(dict4.get("value"), rows.get(1).getValue());
        Assert.assertEquals(dict1.get("key"), rows.get(2).getKey());
        Assert.assertEquals(dict1.get("value"), rows.get(2).getValue());

        // Start/end query without inclusive end:
        options.setInclusiveEnd(false);

        rows = view.queryWithOptions(options);

        expectedRows = new ArrayList<Object>();
        expectedRows.add(dict5);
        expectedRows.add(dict4);

        Assert.assertEquals(2, rows.size());
        Assert.assertEquals(dict5.get("key"), rows.get(0).getKey());
        Assert.assertEquals(dict5.get("value"), rows.get(0).getValue());
        Assert.assertEquals(dict4.get("key"), rows.get(1).getKey());
        Assert.assertEquals(dict4.get("value"), rows.get(1).getValue());

        // Reversed:
        options.setDescending(true);
        options.setStartKey("o");
        options.setEndKey("five");
        options.setInclusiveEnd(true);

        rows = view.queryWithOptions(options);

        expectedRows = new ArrayList<Object>();
        expectedRows.add(dict4);
        expectedRows.add(dict5);

        Assert.assertEquals(2, rows.size());
        Assert.assertEquals(dict4.get("key"), rows.get(0).getKey());
        Assert.assertEquals(dict4.get("value"), rows.get(0).getValue());
        Assert.assertEquals(dict5.get("key"), rows.get(1).getKey());
        Assert.assertEquals(dict5.get("value"), rows.get(1).getValue());

        // Reversed, no inclusive end:
        options.setInclusiveEnd(false);

        rows = view.queryWithOptions(options);

        expectedRows = new ArrayList<Object>();
        expectedRows.add(dict4);

        Assert.assertEquals(1, rows.size());
        Assert.assertEquals(dict4.get("key"), rows.get(0).getKey());
        Assert.assertEquals(dict4.get("value"), rows.get(0).getValue());

        // Specific keys:
        options = new QueryOptions();
        List<Object> keys = new ArrayList<Object>();
        keys.add("two");
        keys.add("four");
        options.setKeys(keys);

        rows = view.queryWithOptions(options);

        expectedRows = new ArrayList<Object>();
        expectedRows.add(dict4);
        expectedRows.add(dict2);

        Assert.assertEquals(2, rows.size());
        Assert.assertEquals(dict4.get("key"), rows.get(0).getKey());
        Assert.assertEquals(dict4.get("value"), rows.get(0).getValue());
        Assert.assertEquals(dict2.get("key"), rows.get(1).getKey());
        Assert.assertEquals(dict2.get("value"), rows.get(1).getValue());

    }

    //https://github.com/couchbase/couchbase-lite-android/issues/314
    public void testViewQueryWithDictSentinel() throws CouchbaseLiteException {

        List<String> key1 = new ArrayList<String>();
        key1.add("red");
        key1.add("model1");
        Map<String, Object> dict1 = new HashMap<String, Object>();
        dict1.put("id", "11");
        dict1.put("key", key1);
        putDoc(database, dict1);

        List<String> key2 = new ArrayList<String>();
        key2.add("red");
        key2.add("model2");
        Map<String, Object> dict2 = new HashMap<String, Object>();
        dict2.put("id", "12");
        dict2.put("key", key2);
        putDoc(database, dict2);

        List<String> key3 = new ArrayList<String>();
        key3.add("green");
        key3.add("model1");
        Map<String, Object> dict3 = new HashMap<String, Object>();
        dict3.put("id", "21");
        dict3.put("key", key3);
        putDoc(database, dict3);

        List<String> key4 = new ArrayList<String>();
        key4.add("yellow");
        key4.add("model2");
        Map<String, Object> dict4 = new HashMap<String, Object>();
        dict4.put("id", "31");
        dict4.put("key", key4);
        putDoc(database, dict4);

        View view = createView(database);

        view.updateIndex();

        // Query all rows:
        QueryOptions options = new QueryOptions();
        List<QueryRow> rows = view.queryWithOptions(options);

        Assert.assertEquals(4, rows.size());
        Assert.assertTrue(Arrays.equals(new Object[]{"green", "model1"}, ((LazyJsonArray) rows.get(0).getKey()).toArray()));
        Assert.assertTrue(Arrays.equals(new Object[]{"red", "model1"}, ((LazyJsonArray) rows.get(1).getKey()).toArray()));
        Assert.assertTrue(Arrays.equals(new Object[]{"red", "model2"}, ((LazyJsonArray) rows.get(2).getKey()).toArray()));
        Assert.assertTrue(Arrays.equals(new Object[]{"yellow", "model2"}, ((LazyJsonArray) rows.get(3).getKey()).toArray()));

        // Start/end key query:
        options = new QueryOptions();
        options.setStartKey("a");
        options.setEndKey(Arrays.asList("red", new HashMap<String, Object>()));
        rows = view.queryWithOptions(options);
        Assert.assertEquals(3, rows.size());
        Assert.assertTrue(Arrays.equals(new Object[]{"green", "model1"}, ((LazyJsonArray) rows.get(0).getKey()).toArray()));
        Assert.assertTrue(Arrays.equals(new Object[]{"red", "model1"}, ((LazyJsonArray) rows.get(1).getKey()).toArray()));
        Assert.assertTrue(Arrays.equals(new Object[]{"red", "model2"}, ((LazyJsonArray) rows.get(2).getKey()).toArray()));

        // Start/end query without inclusive end:
        options.setEndKey(Arrays.asList("red", "model1"));
        options.setInclusiveEnd(false);
        rows = view.queryWithOptions(options);
        Assert.assertEquals(1, rows.size()); //1
        Assert.assertTrue(Arrays.equals(new Object[]{"green", "model1"}, ((LazyJsonArray) rows.get(0).getKey()).toArray()));

        // Reversed:
        options = new QueryOptions();
        options.setStartKey(Arrays.asList("red", new HashMap<String, Object>()));
        options.setEndKey(Arrays.asList("green", "model1"));
        options.setDescending(true);
        rows = view.queryWithOptions(options);
        Assert.assertEquals(3, rows.size()); 
        Assert.assertTrue(Arrays.equals(new Object[]{"red", "model2"}, ((LazyJsonArray) rows.get(0).getKey()).toArray()));
        Assert.assertTrue(Arrays.equals(new Object[]{"red", "model1"}, ((LazyJsonArray) rows.get(1).getKey()).toArray()));
        Assert.assertTrue(Arrays.equals(new Object[]{"green", "model1"}, ((LazyJsonArray) rows.get(2).getKey()).toArray()));

        // Reversed, no inclusive end:
        options.setInclusiveEnd(false);
        rows = view.queryWithOptions(options);
        Assert.assertEquals(2, rows.size());
        Assert.assertTrue(Arrays.equals(new Object[]{"red", "model2"}, ((LazyJsonArray) rows.get(0).getKey()).toArray()));
        Assert.assertTrue(Arrays.equals(new Object[]{"red", "model1"}, ((LazyJsonArray) rows.get(1).getKey()).toArray()));

        // Specific keys:
        options = new QueryOptions();
        List<Object> keys = new ArrayList<Object>();
        keys.add(new Object[]{"red", "model1"});
        keys.add(new Object[]{"red", "model2"});
        options.setKeys(keys);
        rows = view.queryWithOptions(options);
        Assert.assertEquals(2, rows.size());
        Assert.assertTrue(Arrays.equals(new Object[]{"red", "model1"}, ((LazyJsonArray) rows.get(0).getKey()).toArray()));
        Assert.assertTrue(Arrays.equals(new Object[]{"red", "model2"}, ((LazyJsonArray) rows.get(1).getKey()).toArray()));
    }


    /**
     * https://github.com/couchbase/couchbase-lite-android/issues/139
     * test based on https://github.com/couchbase/couchbase-lite-ios/blob/master/Source/CBL_View_Tests.m#L358
     */
    public void testViewQueryStartKeyDocID() throws CouchbaseLiteException {

        putDocs(database);
        List<RevisionInternal> result = new ArrayList<RevisionInternal>();
        Map<String,Object> dict = new HashMap<String,Object>();
        dict.put("_id", "11112");
        dict.put("key", "one");
        result.add(putDoc(database, dict));
        View view = createView(database);

        view.updateIndex();
        QueryOptions options = new QueryOptions();
        options.setStartKey("one");
        options.setStartKeyDocId("11112");
        options.setEndKey("three");
        List<QueryRow> rows = view.queryWithOptions(options);

        assertEquals(2, rows.size());
        assertEquals("11112", rows.get(0).getDocumentId());
        assertEquals("one", rows.get(0).getKey());
        assertEquals("33333", rows.get(1).getDocumentId());
        assertEquals("three", rows.get(1).getKey());

        options = new QueryOptions();
        options.setEndKey("one");
        options.setEndKeyDocId("11111");
        rows = view.queryWithOptions(options);

        Log.d(TAG, "rows: " + rows);
        assertEquals(3, rows.size());
        assertEquals("55555", rows.get(0).getDocumentId());
        assertEquals("five", rows.get(0).getKey());
        assertEquals("44444", rows.get(1).getDocumentId());
        assertEquals("four", rows.get(1).getKey());
        assertEquals("11111", rows.get(2).getDocumentId());
        assertEquals("one", rows.get(2).getKey());

        options.setStartKey("one");
        options.setStartKeyDocId("11111");
        rows = view.queryWithOptions(options);
        assertEquals(1, rows.size());
        assertEquals("11111", rows.get(0).getDocumentId());
        assertEquals("one", rows.get(0).getKey());

    }

    /**
     * https://github.com/couchbase/couchbase-lite-android/issues/260
     */
    public void testViewNumericKeys() throws CouchbaseLiteException {
        Map<String,Object> dict = new HashMap<String,Object>();
        dict.put("_id", "22222");
        dict.put("referenceNumber", 33547239);
        dict.put("title", "this is the title");
        putDoc(database, dict);

        View view = createView(database);

        view.setMap(new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                if (document.containsKey("referenceNumber")){
                    emitter.emit(document.get("referenceNumber"), document);
                }

            }
        }, "1");

        Query query = view.createQuery();
        query.setStartKey(33547239);
        query.setEndKey(33547239);
        QueryEnumerator rows = query.run();
        assertEquals(1, rows.getCount());

        assertEquals(33547239, rows.getRow(0).getKey());
    }

    public void testAllDocsQuery() throws CouchbaseLiteException {

        List<RevisionInternal> docs = putDocs(database);

        List<QueryRow> expectedRow = new ArrayList<QueryRow>();
        for (RevisionInternal rev : docs) {
            Map<String,Object> value = new HashMap<String, Object>();
            value.put("rev", rev.getRevId());
            value.put("_conflicts", new ArrayList<String>());
            QueryRow queryRow = new QueryRow(rev.getDocId(), 0, rev.getDocId(), value, null);
            queryRow.setDatabase(database);
            expectedRow.add(queryRow);
        }

        QueryOptions options = new QueryOptions();
        Map<String,Object> allDocs = database.getAllDocs(options);

        List<QueryRow> expectedRows = new ArrayList<QueryRow>();
        expectedRows.add(expectedRow.get(2));
        expectedRows.add(expectedRow.get(0));
        expectedRows.add(expectedRow.get(3));
        expectedRows.add(expectedRow.get(1));
        expectedRows.add(expectedRow.get(4));

        Map<String,Object> expectedQueryResult = createExpectedQueryResult(expectedRows, 0);

        Assert.assertEquals(expectedQueryResult, allDocs);

        // Start/end key query:
        options = new QueryOptions();
        options.setStartKey("2");
        options.setEndKey("44444");

        allDocs = database.getAllDocs(options);

        expectedRows = new ArrayList<QueryRow>();
        expectedRows.add(expectedRow.get(0));
        expectedRows.add(expectedRow.get(3));
        expectedRows.add(expectedRow.get(1));

        expectedQueryResult = createExpectedQueryResult(expectedRows, 0);
        Assert.assertEquals(expectedQueryResult, allDocs);

        // Start/end query without inclusive end:
        options.setInclusiveEnd(false);

        allDocs = database.getAllDocs(options);

        expectedRows = new ArrayList<QueryRow>();
        expectedRows.add(expectedRow.get(0));
        expectedRows.add(expectedRow.get(3));

        expectedQueryResult = createExpectedQueryResult(expectedRows, 0);
        Assert.assertEquals(expectedQueryResult, allDocs);

        // Get all documents: with default QueryOptions
        options = new QueryOptions();
        allDocs = database.getAllDocs(options);

        expectedRows = new ArrayList<QueryRow>();
        expectedRows.add(expectedRow.get(2));
        expectedRows.add(expectedRow.get(0));
        expectedRows.add(expectedRow.get(3));
        expectedRows.add(expectedRow.get(1));
        expectedRows.add(expectedRow.get(4));
        expectedQueryResult = createExpectedQueryResult(expectedRows, 0);

        Assert.assertEquals(expectedQueryResult, allDocs);

        // Get specific documents:
        options = new QueryOptions();
        List<Object> docIds = new ArrayList<Object>();
        QueryRow expected2 = expectedRow.get(2);
        docIds.add(expected2.getDocument().getId());
        options.setKeys(docIds);
        allDocs = database.getAllDocs(options);
        expectedRows = new ArrayList<QueryRow>();
        expectedRows.add(expected2);
        expectedQueryResult = createExpectedQueryResult(expectedRows, 0);
        Assert.assertEquals(expectedQueryResult, allDocs);
    }

    private Map<String, Object> createExpectedQueryResult(List<QueryRow> rows, int offset) {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("rows", rows);
        result.put("total_rows", rows.size());
        result.put("offset", offset);
        return result;
    }

    /**
     * TODO: It seems this test is not correct and also LiveQuery is not correctly implemented. Fix this!!
     *
     * NOTE: ChangeNotification should not be fired for 0 match query.
     */
    public void failingTestAllDocumentsLiveQuery() throws CouchbaseLiteException {
        final AtomicInteger changeCount = new AtomicInteger();

        Database db = startDatabase();
        LiveQuery query = db.createAllDocumentsQuery().toLiveQuery();
        query.setStartKey("1");
        query.setEndKey("10");
        query.addChangeListener(new LiveQuery.ChangeListener() {
            @Override
            public void changed(LiveQuery.ChangeEvent event) {
                changeCount.incrementAndGet();
            }
        });

        query.start();
        query.waitForRows();
        assertNull(query.getLastError());
        QueryEnumerator rows = query.getRows();
        assertNotNull(rows);
        assertEquals(0, rows.getCount());

        // A change event is sent the first time a query finishes loading
        assertEquals(1, changeCount.get());

        db.getDocument("a").createRevision().save();

        query.waitForRows();
        // A change event is not sent, if the query results remain the same
        assertEquals(1, changeCount.get());
        rows = query.getRows();
        assertNotNull(rows);
        assertEquals(0, rows.getCount());

        db.getDocument("1").createRevision().save();

        // The query must update before sending notifications
        assertEquals(1, changeCount.get());
        query.waitForRows();
        assertEquals(2, changeCount.get());

        rows = query.getRows();
        assertNotNull(rows);
        assertEquals(1, rows.getCount());

        assertNull(query.getLastError());
        query.stop();
    }

    public void testViewReduce() throws CouchbaseLiteException {

        Map<String,Object> docProperties1 = new HashMap<String,Object>();
        docProperties1.put("_id", "CD");
        docProperties1.put("cost", 8.99);
        putDoc(database, docProperties1);

        Map<String,Object> docProperties2 = new HashMap<String,Object>();
        docProperties2.put("_id", "App");
        docProperties2.put("cost", 1.95);
        putDoc(database, docProperties2);

        Map<String,Object> docProperties3 = new HashMap<String,Object>();
        docProperties3.put("_id", "Dessert");
        docProperties3.put("cost", 6.50);
        putDoc(database, docProperties3);

        View view = database.getView("totaler");
        view.setMapReduce(new Mapper() {

                              @Override
                              public void map(Map<String, Object> document, Emitter emitter) {
                                  Assert.assertNotNull(document.get("_id"));
                                  Assert.assertNotNull(document.get("_rev"));
                                  Object cost = document.get("cost");
                                  if (cost != null) {
                                      emitter.emit(document.get("_id"), cost);
                                  }
                              }
                          }, new Reducer() {

                              @Override
                              public Object reduce(List<Object> keys, List<Object> values,
                                                   boolean rereduce) {
                                  return View.totalValues(values);
                              }
                          }, "1"
        );

        view.updateIndex();

        List<Map<String,Object>> dumpResult = view.dump();
        Log.v(TAG, "View dump: " + dumpResult);
        Assert.assertEquals(3, dumpResult.size());
        Assert.assertEquals("\"App\"", dumpResult.get(0).get("key"));
        Assert.assertEquals("1.95", dumpResult.get(0).get("value"));
        Assert.assertEquals(2, dumpResult.get(0).get("seq"));
        Assert.assertEquals("\"CD\"", dumpResult.get(1).get("key"));
        Assert.assertEquals("8.99", dumpResult.get(1).get("value"));
        Assert.assertEquals(1, dumpResult.get(1).get("seq"));
        Assert.assertEquals("\"Dessert\"", dumpResult.get(2).get("key"));
        Assert.assertEquals("6.5", dumpResult.get(2).get("value"));
        Assert.assertEquals(3, dumpResult.get(2).get("seq"));

        QueryOptions options = new QueryOptions();
        options.setReduce(true);
        List<QueryRow> reduced = view.queryWithOptions(options);
        Assert.assertEquals(1, reduced.size());
        Object value = reduced.get(0).getValue();
        Number numberValue = (Number)value;
        Assert.assertTrue(Math.abs(numberValue.doubleValue() - 17.44) < 0.001);

    }

    public void testIndexUpdateMode() throws CouchbaseLiteException {

        View view = createView(database);
        Query query = view.createQuery();
        query.setIndexUpdateMode(Query.IndexUpdateMode.BEFORE);
        int numRowsBefore = query.run().getCount();
        assertEquals(0, numRowsBefore);

        // do a query and force re-indexing, number of results should be +4
        putNDocs(database, 1);
        query.setIndexUpdateMode(Query.IndexUpdateMode.BEFORE);
        assertEquals(1, query.run().getCount());

        // do a query without re-indexing, number of results should be the same
        putNDocs(database, 4);
        query.setIndexUpdateMode(Query.IndexUpdateMode.NEVER);
        assertEquals(1, query.run().getCount());

        // do a query and force re-indexing, number of results should be +4
        query.setIndexUpdateMode(Query.IndexUpdateMode.BEFORE);
        assertEquals(5, query.run().getCount());

        // do a query which will kick off an async index
        putNDocs(database, 1);
        query.setIndexUpdateMode(Query.IndexUpdateMode.AFTER);
        query.run().getCount();

        // wait until indexing is (hopefully) done
        try {
            Thread.sleep(1 * 1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        assertEquals(6, query.run().getCount());

    }

    public void testViewGrouped() throws CouchbaseLiteException {

        Map<String,Object> docProperties1 = new HashMap<String,Object>();
        docProperties1.put("_id", "1");
        docProperties1.put("artist", "Gang Of Four");
        docProperties1.put("album", "Entertainment!");
        docProperties1.put("track", "Ether");
        docProperties1.put("time", 231);
        putDoc(database, docProperties1);

        Map<String,Object> docProperties2 = new HashMap<String,Object>();
        docProperties2.put("_id", "2");
        docProperties2.put("artist", "Gang Of Four");
        docProperties2.put("album", "Songs Of The Free");
        docProperties2.put("track", "I Love A Man In Uniform");
        docProperties2.put("time", 248);
        putDoc(database, docProperties2);

        Map<String,Object> docProperties3 = new HashMap<String,Object>();
        docProperties3.put("_id", "3");
        docProperties3.put("artist", "Gang Of Four");
        docProperties3.put("album", "Entertainment!");
        docProperties3.put("track", "Natural's Not In It");
        docProperties3.put("time", 187);
        putDoc(database, docProperties3);

        Map<String,Object> docProperties4 = new HashMap<String,Object>();
        docProperties4.put("_id", "4");
        docProperties4.put("artist", "PiL");
        docProperties4.put("album", "Metal Box");
        docProperties4.put("track", "Memories");
        docProperties4.put("time", 309);
        putDoc(database, docProperties4);

        Map<String,Object> docProperties5 = new HashMap<String,Object>();
        docProperties5.put("_id", "5");
        docProperties5.put("artist", "Gang Of Four");
        docProperties5.put("album", "Entertainment!");
        docProperties5.put("track", "Not Great Men");
        docProperties5.put("time", 187);
        putDoc(database, docProperties5);

        View view = database.getView("grouper");
        view.setMapReduce(new Mapper() {

                              @Override
                              public void map(Map<String, Object> document, Emitter emitter) {
                                  List<Object> key = new ArrayList<Object>();
                                  key.add(document.get("artist"));
                                  key.add(document.get("album"));
                                  key.add(document.get("track"));
                                  emitter.emit(key, document.get("time"));
                              }
                          }, new Reducer() {

                              @Override
                              public Object reduce(List<Object> keys, List<Object> values,
                                                   boolean rereduce) {
                                  return View.totalValues(values);
                              }
                          }, "1"
        );

        Status status = new Status();
        view.updateIndex();

        QueryOptions options = new QueryOptions();
        options.setReduce(true);
        List<QueryRow> rows = view.queryWithOptions(options);

        List<Map<String,Object>> expectedRows = new ArrayList<Map<String,Object>>();
        Map<String,Object> row1 = new HashMap<String,Object>();
        row1.put("key", null);
        row1.put("value", 1162.0);
        expectedRows.add(row1);

        Assert.assertEquals(row1.get("key"), rows.get(0).getKey());
        Assert.assertEquals(row1.get("value"), rows.get(0).getValue());

        //now group
        options.setGroup(true);
        status = new Status();
        rows = view.queryWithOptions(options);

        expectedRows = new ArrayList<Map<String,Object>>();

        row1 = new HashMap<String,Object>();
        List<String> key1 = new ArrayList<String>();
        key1.add("Gang Of Four");
        key1.add("Entertainment!");
        key1.add("Ether");
        row1.put("key", key1);
        row1.put("value", 231.0);
        expectedRows.add(row1);

        Map<String,Object> row2 = new HashMap<String,Object>();
        List<String> key2 = new ArrayList<String>();
        key2.add("Gang Of Four");
        key2.add("Entertainment!");
        key2.add("Natural's Not In It");
        row2.put("key", key2);
        row2.put("value", 187.0);
        expectedRows.add(row2);

        Map<String,Object> row3 = new HashMap<String,Object>();
        List<String> key3 = new ArrayList<String>();
        key3.add("Gang Of Four");
        key3.add("Entertainment!");
        key3.add("Not Great Men");
        row3.put("key", key3);
        row3.put("value", 187.0);
        expectedRows.add(row3);

        Map<String,Object> row4 = new HashMap<String,Object>();
        List<String> key4 = new ArrayList<String>();
        key4.add("Gang Of Four");
        key4.add("Songs Of The Free");
        key4.add("I Love A Man In Uniform");
        row4.put("key", key4);
        row4.put("value", 248.0);
        expectedRows.add(row4);

        Map<String,Object> row5 = new HashMap<String,Object>();
        List<String> key5 = new ArrayList<String>();
        key5.add("PiL");
        key5.add("Metal Box");
        key5.add("Memories");
        row5.put("key", key5);
        row5.put("value", 309.0);
        expectedRows.add(row5);

        Assert.assertEquals(row1.get("key"), rows.get(0).getKey());
        Assert.assertEquals(row1.get("value"), rows.get(0).getValue());
        Assert.assertEquals(row2.get("key"), rows.get(1).getKey());
        Assert.assertEquals(row2.get("value"), rows.get(1).getValue());
        Assert.assertEquals(row3.get("key"), rows.get(2).getKey());
        Assert.assertEquals(row3.get("value"), rows.get(2).getValue());
        Assert.assertEquals(row4.get("key"), rows.get(3).getKey());
        Assert.assertEquals(row4.get("value"), rows.get(3).getValue());
        Assert.assertEquals(row5.get("key"), rows.get(4).getKey());
        Assert.assertEquals(row5.get("value"), rows.get(4).getValue());

        //group level 1
        options.setGroupLevel(1);
        status = new Status();
        rows = view.queryWithOptions(options);

        expectedRows = new ArrayList<Map<String,Object>>();

        row1 = new HashMap<String,Object>();
        key1 = new ArrayList<String>();
        key1.add("Gang Of Four");
        row1.put("key", key1);
        row1.put("value", 853.0);
        expectedRows.add(row1);

        row2 = new HashMap<String,Object>();
        key2 = new ArrayList<String>();
        key2.add("PiL");
        row2.put("key", key2);
        row2.put("value", 309.0);
        expectedRows.add(row2);

        Assert.assertEquals(row1.get("key"), rows.get(0).getKey());
        Assert.assertEquals(row1.get("value"), rows.get(0).getValue());
        Assert.assertEquals(row2.get("key"), rows.get(1).getKey());
        Assert.assertEquals(row2.get("value"), rows.get(1).getValue());

        //group level 2
        options.setGroupLevel(2);
        status = new Status();
        rows = view.queryWithOptions(options);

        expectedRows = new ArrayList<Map<String,Object>>();

        row1 = new HashMap<String,Object>();
        key1 = new ArrayList<String>();
        key1.add("Gang Of Four");
        key1.add("Entertainment!");
        row1.put("key", key1);
        row1.put("value", 605.0);
        expectedRows.add(row1);

        row2 = new HashMap<String,Object>();
        key2 = new ArrayList<String>();
        key2.add("Gang Of Four");
        key2.add("Songs Of The Free");
        row2.put("key", key2);
        row2.put("value", 248.0);
        expectedRows.add(row2);

        row3 = new HashMap<String,Object>();
        key3 = new ArrayList<String>();
        key3.add("PiL");
        key3.add("Metal Box");
        row3.put("key", key3);
        row3.put("value", 309.0);
        expectedRows.add(row3);

        Assert.assertEquals(row1.get("key"), rows.get(0).getKey());
        Assert.assertEquals(row1.get("value"), rows.get(0).getValue());
        Assert.assertEquals(row2.get("key"), rows.get(1).getKey());
        Assert.assertEquals(row2.get("value"), rows.get(1).getValue());
        Assert.assertEquals(row3.get("key"), rows.get(2).getKey());
        Assert.assertEquals(row3.get("value"), rows.get(2).getValue());

    }

    public void testViewGroupedStrings() throws CouchbaseLiteException {

        Map<String,Object> docProperties1 = new HashMap<String,Object>();
        docProperties1.put("name", "Alice");
        putDoc(database, docProperties1);

        Map<String,Object> docProperties2 = new HashMap<String,Object>();
        docProperties2.put("name", "Albert");
        putDoc(database, docProperties2);

        Map<String,Object> docProperties3 = new HashMap<String,Object>();
        docProperties3.put("name", "Naomi");
        putDoc(database, docProperties3);

        Map<String,Object> docProperties4 = new HashMap<String,Object>();
        docProperties4.put("name", "Jens");
        putDoc(database, docProperties4);

        Map<String,Object> docProperties5 = new HashMap<String,Object>();
        docProperties5.put("name", "Jed");
        putDoc(database, docProperties5);

        View view = database.getView("default/names");
        view.setMapReduce(new Mapper() {

                              @Override
                              public void map(Map<String, Object> document, Emitter emitter) {
                                  String name = (String) document.get("name");
                                  if (name != null) {
                                      emitter.emit(name.substring(0, 1), 1);
                                  }
                              }

                          }, new Reducer() {

                              @Override
                              public Object reduce(List<Object> keys, List<Object> values,
                                                   boolean rereduce) {
                                  return values.size();
                              }

                          }, "1.0"
        );


        view.updateIndex();

        QueryOptions options = new QueryOptions();
        options.setGroupLevel(1);
        List<QueryRow> rows = view.queryWithOptions(options);

        List<Map<String,Object>> expectedRows = new ArrayList<Map<String,Object>>();
        Map<String,Object> row1 = new HashMap<String,Object>();
        row1.put("key", "A");
        row1.put("value", 2);
        expectedRows.add(row1);
        Map<String,Object> row2 = new HashMap<String,Object>();
        row2.put("key", "J");
        row2.put("value", 2);
        expectedRows.add(row2);
        Map<String,Object> row3 = new HashMap<String,Object>();
        row3.put("key", "N");
        row3.put("value", 1);
        expectedRows.add(row3);

        Assert.assertEquals(row1.get("key"), rows.get(0).getKey());
        Assert.assertEquals(row1.get("value"), rows.get(0).getValue());
        Assert.assertEquals(row2.get("key"), rows.get(1).getKey());
        Assert.assertEquals(row2.get("value"), rows.get(1).getValue());
        Assert.assertEquals(row3.get("key"), rows.get(2).getKey());
        Assert.assertEquals(row3.get("value"), rows.get(2).getValue());

    }

    public void testViewGroupedNoReduce() throws CouchbaseLiteException {
        Map<String,Object> docProperties = new HashMap<String,Object>();
        docProperties.put("_id", "1");
        docProperties.put("type", "A");
        putDoc(database, docProperties);

        docProperties = new HashMap<String,Object>();
        docProperties.put("_id", "2");
        docProperties.put("type", "A");
        putDoc(database, docProperties);

        docProperties = new HashMap<String,Object>();
        docProperties.put("_id", "3");
        docProperties.put("type", "B");
        putDoc(database, docProperties);

        docProperties = new HashMap<String,Object>();
        docProperties.put("_id", "4");
        docProperties.put("type", "B");
        putDoc(database, docProperties);

        docProperties = new HashMap<String,Object>();
        docProperties.put("_id", "5");
        docProperties.put("type", "C");
        putDoc(database, docProperties);

        docProperties = new HashMap<String,Object>();
        docProperties.put("_id", "6");
        docProperties.put("type", "C");
        putDoc(database, docProperties);

        View view = database.getView("GroupByType");
        view.setMap(new Mapper() {
                              @Override
                              public void map(Map<String, Object> document, Emitter emitter) {
                                  String type = (String) document.get("type");
                                  if (type != null) {
                                      emitter.emit(type, null);
                                  }
                              }

                          }, "1.0"
        );

        view.updateIndex();
        QueryOptions options = new QueryOptions();
        //setGroup without reduce function
        options.setGroupLevel(1);
        List<QueryRow> rows = view.queryWithOptions(options);

        assertEquals(3, rows.size());

        Map<String,Object> row1 = new HashMap<String,Object>();
        row1.put("key", "A");
        row1.put("error", "not_found");

        Map<String,Object> row2 = new HashMap<String,Object>();
        row2.put("key", "B");
        row2.put("error", "not_found");

        Map<String,Object> row3 = new HashMap<String,Object>();
        row3.put("key", "C");
        row3.put("error", "not_found");

        Assert.assertEquals(row1.get("key"), rows.get(0).getKey());
        Assert.assertEquals(row1.get("error"), rows.get(0).asJSONDictionary().get("error"));
        Assert.assertEquals(row2.get("key"), rows.get(1).getKey());
        Assert.assertEquals(row2.get("error"), rows.get(1).asJSONDictionary().get("error"));
        Assert.assertEquals(row3.get("key"), rows.get(2).getKey());
        Assert.assertEquals(row3.get("error"), rows.get(2).asJSONDictionary().get("error"));
    }

    /**
     * https://github.com/couchbase/couchbase-lite-java-core/issues/413
     */
    public void testViewGroupedNoReduceWithoutDocs() throws CouchbaseLiteException {
        View view = database.getView("GroupByType");
        view.setMap(new Mapper() {
                        @Override
                        public void map(Map<String, Object> document, Emitter emitter) {
                            String type = (String) document.get("type");
                            if (type != null) {
                                emitter.emit(type, null);
                            }
                        }
                    }, "1.0"
        );
        view.updateIndex();
        QueryOptions options = new QueryOptions();
        options.setGroupLevel(1);
        List<QueryRow> rows = view.queryWithOptions(options);
        assertEquals(0, rows.size());
    }

    public void testViewGroupedVariableLengthKey() throws CouchbaseLiteException {
        Map<String,Object> docProperties1 = new HashMap<String,Object>();
        docProperties1.put("_id", "H");
        docProperties1.put("atomic_number", 1);
        docProperties1.put("name", "Hydrogen");
        docProperties1.put("electrons", new Integer[] {1});
        putDoc(database, docProperties1);

        Map<String,Object> docProperties2 = new HashMap<String,Object>();
        docProperties2.put("_id", "He");
        docProperties2.put("atomic_number", 2);
        docProperties2.put("name", "Helium");
        docProperties2.put("electrons", new Integer[] {2});
        putDoc(database, docProperties2);

        Map<String,Object> docProperties3 = new HashMap<String,Object>();
        docProperties3.put("_id", "Ne");
        docProperties3.put("atomic_number", 10);
        docProperties3.put("name", "Neon");
        docProperties3.put("electrons", new Integer[] {2, 8});
        putDoc(database, docProperties3);

        Map<String,Object> docProperties4 = new HashMap<String,Object>();
        docProperties4.put("_id", "Na");
        docProperties4.put("atomic_number", 11);
        docProperties4.put("name", "Sodium");
        docProperties4.put("electrons", new Integer[] {2, 8, 1});
        putDoc(database, docProperties4);

        Map<String,Object> docProperties5 = new HashMap<String,Object>();
        docProperties5.put("_id", "Mg");
        docProperties5.put("atomic_number", 12);
        docProperties5.put("name", "Magnesium");
        docProperties5.put("electrons", new Integer[] {2, 8, 2});
        putDoc(database, docProperties5);
        
        Map<String,Object> docProperties6 = new HashMap<String,Object>();
        docProperties6.put("_id", "Cr");
        docProperties6.put("atomic_number", 24);
        docProperties6.put("name", "Chromium");
        docProperties6.put("electrons", new Integer[] {2, 8, 13, 1});
        putDoc(database, docProperties6);
        
        Map<String,Object> docProperties7 = new HashMap<String,Object>();
        docProperties7.put("_id", "Zn");
        docProperties7.put("atomic_number", 30);
        docProperties7.put("name", "Zinc");
        docProperties7.put("electrons", new Integer[] {2, 8, 18, 2});
        putDoc(database, docProperties7);
        
        /*
            expected key-value pairs at group level 2:
              [1] -> 1
              [2] -> 1
              [2, 8] -> 5
        */
        
        View view = database.getView("electrons");
        view.setMapReduce(new Mapper() {

                              @Override
                              public void map(Map<String, Object> document, Emitter emitter) {
                                  emitter.emit(document.get("electrons"), 1);
                              }
                          }, new Reducer() {

                              @Override
                              public Object reduce(List<Object> keys, List<Object> values,
                                                   boolean rereduce) {
                                  return View.totalValues(values);
                              }
                          }, "1"
        );
                          
        view.updateIndex();

        QueryOptions options = new QueryOptions();
        options.setReduce(true);
        options.setGroupLevel(2);
        List<QueryRow> rows = view.queryWithOptions(options);

        assertEquals(3, rows.size());
        
        List<Map<String,Object>> expectedRows = new ArrayList<Map<String,Object>>();
        Map<String,Object> row1 = new HashMap<String,Object>();
        row1.put("key", Arrays.asList(new Integer[] {1}));
        row1.put("value", 1.0);
        expectedRows.add(row1);
        Map<String,Object> row2 = new HashMap<String,Object>();
        row2.put("key", Arrays.asList(new Integer[] {2}));
        row2.put("value", 1.0);
        expectedRows.add(row2);
        Map<String,Object> row3 = new HashMap<String,Object>();
        row3.put("key", Arrays.asList(new Integer[] {2,8}));
        row3.put("value", 5.0);
        expectedRows.add(row3);
        
        Assert.assertEquals(row1.get("key"), rows.get(0).getKey());
        Assert.assertEquals(row1.get("value"), rows.get(0).getValue());
        Assert.assertEquals(row2.get("key"), rows.get(1).getKey());
        Assert.assertEquals(row2.get("value"), rows.get(1).getValue());
        Assert.assertEquals(row3.get("key"), rows.get(2).getKey());
        Assert.assertEquals(row3.get("value"), rows.get(2).getValue());
    }

    

    public void testViewCollation() throws CouchbaseLiteException {
        List<Object> list1 = new ArrayList<Object>();
        list1.add("a");

        List<Object> list2 = new ArrayList<Object>();
        list2.add("b");

        List<Object> list3 = new ArrayList<Object>();
        list3.add("b");
        list3.add("c");

        List<Object> list4 = new ArrayList<Object>();
        list4.add("b");
        list4.add("c");
        list4.add("a");

        List<Object> list5 = new ArrayList<Object>();
        list5.add("b");
        list5.add("d");

        List<Object> list6 = new ArrayList<Object>();
        list6.add("b");
        list6.add("d");
        list6.add("e");


        // Based on CouchDB's "view_collation.js" test
        List<Object> testKeys = new ArrayList<Object>();
        testKeys.add(null);
        testKeys.add(false);
        testKeys.add(true);
        testKeys.add(0);
        testKeys.add(2.5);
        testKeys.add(10);
        testKeys.add(" ");
        testKeys.add("_");
        testKeys.add("~");
        testKeys.add("a");
        testKeys.add("A");
        testKeys.add("aa");
        testKeys.add("b");
        testKeys.add("B");
        testKeys.add("ba");
        testKeys.add("bb");
        testKeys.add(list1);
        testKeys.add(list2);
        testKeys.add(list3);
        testKeys.add(list4);
        testKeys.add(list5);
        testKeys.add(list6);

        int i=0;
        for (Object key : testKeys) {
            Map<String,Object> docProperties = new HashMap<String,Object>();
            docProperties.put("_id", Integer.toString(i++));
            docProperties.put("name", key);
            putDoc(database, docProperties);
        }

        View view = database.getView("default/names");
        view.setMapReduce(new Mapper() {

            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                emitter.emit(document.get("name"), null);
            }

        }, null, "1.0");

        QueryOptions options = new QueryOptions();
        List<QueryRow> rows = view.queryWithOptions(options);
        i = 0;
        for (QueryRow row : rows) {
            Assert.assertEquals(testKeys.get(i++), row.getKey());
        }
    }


    public void testViewCollationRaw() throws CouchbaseLiteException {
        List<Object> list1 = new ArrayList<Object>();
        list1.add("a");

        List<Object> list2 = new ArrayList<Object>();
        list2.add("b");

        List<Object> list3 = new ArrayList<Object>();
        list3.add("b");
        list3.add("c");

        List<Object> list4 = new ArrayList<Object>();
        list4.add("b");
        list4.add("c");
        list4.add("a");

        List<Object> list5 = new ArrayList<Object>();
        list5.add("b");
        list5.add("d");

        List<Object> list6 = new ArrayList<Object>();
        list6.add("b");
        list6.add("d");
        list6.add("e");


        // Based on CouchDB's "view_collation.js" test
        List<Object> testKeys = new ArrayList<Object>();
        testKeys.add(0);
        testKeys.add(2.5);
        testKeys.add(10);
        testKeys.add(false);
        testKeys.add(null);
        testKeys.add(true);
        testKeys.add(list1);
        testKeys.add(list2);
        testKeys.add(list3);
        testKeys.add(list4);
        testKeys.add(list5);
        testKeys.add(list6);
        testKeys.add(" ");
        testKeys.add("A");
        testKeys.add("B");
        testKeys.add("_");
        testKeys.add("a");
        testKeys.add("aa");
        testKeys.add("b");
        testKeys.add("ba");
        testKeys.add("bb");
        testKeys.add("~");

        int i=0;
        for (Object key : testKeys) {
            Map<String,Object> docProperties = new HashMap<String,Object>();
            docProperties.put("_id", Integer.toString(i++));
            docProperties.put("name", key);
            putDoc(database, docProperties);
        }

        View view = database.getView("default/names");
        view.setMapReduce(new Mapper() {

            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                emitter.emit(document.get("name"), null);
            }

        }, null, "1.0");

        view.setCollation(TDViewCollation.TDViewCollationRaw);

        QueryOptions options = new QueryOptions();

        List<QueryRow> rows = view.queryWithOptions(options);
        i = 0;
        for (QueryRow row : rows) {
            Assert.assertEquals(testKeys.get(i++), row.getKey());
        }

        database.close();
    }

    public void testLargerViewQuery() throws CouchbaseLiteException {
        putNDocs(database, 4);
        View view = createView(database);

        view.updateIndex();

        // Query all rows:
        QueryOptions options = new QueryOptions();
        Status status = new Status();
        List<QueryRow> rows = view.queryWithOptions(options);
    }

    public void testViewLinkedDocs() throws CouchbaseLiteException {
        putLinkedDocs(database);
        
        View view = database.getView("linked");
        view.setMapReduce(new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                if (document.containsKey("value")) {
                    emitter.emit(new Object[]{document.get("value"), 0}, null);
                }
                if (document.containsKey("ancestors")) {
                    List<Object> ancestors = (List<Object>) document.get("ancestors");
                    for (int i = 0; i < ancestors.size(); i++) {
                        Map<String, Object> value = new HashMap<String, Object>();
                        value.put("_id", ancestors.get(i));
                        emitter.emit(new Object[]{document.get("value"), i + 1}, value);
                    }
                }
            }
        }, null, "1");
        
        view.updateIndex();

        QueryOptions options = new QueryOptions();
        options.setIncludeDocs(true);  // required for linked documents
        
        List<QueryRow> rows = view.queryWithOptions(options);
        
        Assert.assertNotNull(rows);
        Assert.assertEquals(5, rows.size());

        Object[][] expected = new Object[][] {
                /* id, key0, key1, value._id, doc._id */
                new Object[] { "22222", "hello", 0, null, "22222" },
                new Object[] { "22222", "hello", 1, "11111", "11111" },
                new Object[] { "33333", "world", 0, null, "33333" },
                new Object[] { "33333", "world", 1, "22222", "22222" },
                new Object[] { "33333", "world", 2, "11111", "11111" },
        };

        for (int i=0; i < rows.size(); i++) {
            QueryRow row = rows.get(i);

            Map<String, Object> rowAsJson = row.asJSONDictionary();
            Log.d(TAG, "" + rowAsJson);
            List<Object> key = (List<Object>) rowAsJson.get("key");
            Map<String,Object> doc = (Map<String,Object>) rowAsJson.get("doc");
            String id = (String) rowAsJson.get("id");

            Assert.assertEquals(expected[i][0], id);
            Assert.assertEquals(2, key.size());
            Assert.assertEquals(expected[i][1], key.get(0));
            Assert.assertEquals(expected[i][2], key.get(1));
            if (expected[i][3] == null) {
                Assert.assertNull(row.getValue());
            }
            else {
                Assert.assertEquals(expected[i][3], ((Map<String, Object>) row.getValue()).get("_id"));
            }
            Assert.assertEquals(expected[i][4], doc.get("_id")); 

        }
    }

    /**
     * https://github.com/couchbase/couchbase-lite-java-core/issues/29
     */
    public void testRunLiveQueriesWithReduce() throws Exception {

        final Database db = startDatabase();
        // run a live query
        View view = db.getView("vu");
        view.setMapReduce(new Mapper() {
                              @Override
                              public void map(Map<String, Object> document, Emitter emitter) {
                                  emitter.emit(document.get("sequence"), 1);
                              }
                          }, new Reducer() {
                              @Override
                              public Object reduce(List<Object> keys, List<Object> values, boolean rereduce) {
                                  return View.totalValues(values);
                              }
                          },
                "1"
        );
        final LiveQuery query = view.createQuery().toLiveQuery();

        View view1 = db.getView("vu1");
        view1.setMapReduce(new Mapper() {
                               @Override
                               public void map(Map<String, Object> document, Emitter emitter) {
                                   emitter.emit(document.get("sequence"), 1);
                               }
                           }, new Reducer() {
                               @Override
                               public Object reduce(List<Object> keys, List<Object> values, boolean rereduce) {
                                   return View.totalValues(values);
                               }
                           },
                "1"
        );
        final LiveQuery query1 = view1.createQuery().toLiveQuery();

        final int kNDocs = 10;
        createDocumentsAsync(db, kNDocs);

        assertNull(query.getRows());
        query.start();

        final CountDownLatch gotExpectedQueryResult = new CountDownLatch(1);

        query.addChangeListener(new LiveQuery.ChangeListener() {
            @Override
            public void changed(LiveQuery.ChangeEvent event) {
                if (event.getError() != null) {
                    Log.e(TAG, "LiveQuery change event had error", event.getError());
                } else if (event.getRows().getCount() == 1 && ((Double) event.getRows().getRow(0).getValue()).intValue() == kNDocs) {
                    gotExpectedQueryResult.countDown();
                }
            }
        });
        boolean success = gotExpectedQueryResult.await(30, TimeUnit.SECONDS);
        Assert.assertTrue(success);

        query.stop();


        query1.start();

        createDocumentsAsync(db, kNDocs + 5);//10 + 10 + 5

        final CountDownLatch gotExpectedQuery1Result = new CountDownLatch(1);
        query1.addChangeListener(new LiveQuery.ChangeListener() {
            @Override
            public void changed(LiveQuery.ChangeEvent event) {
                if (event.getError() != null) {
                    Log.e(TAG, "LiveQuery change event had error", event.getError());
                } else if (event.getRows().getCount() == 1 && ((Double) event.getRows().getRow(0).getValue()).intValue() == 2*kNDocs + 5) {
                    gotExpectedQuery1Result.countDown();
                }
            }
        });
        success = gotExpectedQuery1Result.await(30, TimeUnit.SECONDS);
        Assert.assertTrue(success);

        query1.stop();

        assertEquals(2*kNDocs + 5, db.getDocumentCount()); // 25 - OK


    }

    private SavedRevision createTestRevisionNoConflicts(Document doc, String val) throws Exception {
        UnsavedRevision unsavedRev = doc.createRevision();
        Map<String,Object> props = new HashMap<String,Object>();
        props.put("key", val);
        unsavedRev.setUserProperties(props);
        return unsavedRev.save();
    }

    /**
     * https://github.com/couchbase/couchbase-lite-java-core/issues/131
     */
    public void testViewWithConflict() throws Exception {

        // Create doc and add some revs
        Document doc = database.createDocument();
        SavedRevision rev1 = createTestRevisionNoConflicts(doc, "1");
        SavedRevision rev2a = createTestRevisionNoConflicts(doc, "2a");
        SavedRevision rev3 = createTestRevisionNoConflicts(doc, "3");

        // index the view
        View view = createView(database);
        QueryEnumerator rows = view.createQuery().run();

        assertEquals(1, rows.getCount());
        QueryRow row = rows.next();
        assertEquals(row.getKey(), "3");
        // assertNotNull(row.getDocumentRevisionId()); -- TODO: why is this null?

        // Create a conflict
        UnsavedRevision rev2bUnsaved = rev1.createRevision();
        Map<String,Object> props = new HashMap<String,Object>();
        props.put("key", "2b");
        rev2bUnsaved.setUserProperties(props);
        SavedRevision rev2b = rev2bUnsaved.save(true);

        // re-run query
        view.updateIndex();
        rows = view.createQuery().run();

        // we should only see one row, with key=3.
        // if we see key=2b then it's a bug.
        assertEquals(1, rows.getCount());
        row = rows.next();
        assertEquals(row.getKey(), "3");

    }

    /**
     * https://github.com/couchbase/couchbase-lite-java-core/issues/226
     */
    public void testViewSecondQuery() throws Exception {

        // Create doc and add some revs
        final Document doc = database.createDocument();
        String jsonString = "{\n" +
                "    \"name\":\"praying mantis\",\n" +
                "    \"wikipedia\":{\n" +
                "        \"behavior\":{\n" +
                "            \"style\":\"predatory\",\n" +
                "            \"attack\":\"ambush\"\n" +
                "        },\n" +
                "        \"evolution\":{\n" +
                "            \"ancestor\":\"proto-roaches\",\n" +
                "            \"cousin\":\"termite\"\n" +
                "        }       \n" +
                "    }   \n" +
                "\n" +
                "}";

        Map jsonObject = (Map) Manager.getObjectMapper().readValue(jsonString, Object.class);
        doc.putProperties(jsonObject);

        View view = database.getView("testViewSecondQueryView");
        view.setMapReduce(new Mapper() {

            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                if (document.get("name") != null) {
                    emitter.emit(document.get("name"), document);
                }
            }
        }, null, "1");


        for (int i=0; i<2; i++) {

            Query query = view.createQuery();
            QueryEnumerator rows = query.run();

            for (Iterator<QueryRow> it = rows; it.hasNext(); ) {
                QueryRow row = it.next();
                Map wikipediaField = (Map) row.getDocument().getProperty("wikipedia");
                assertTrue(wikipediaField.containsKey("behavior"));
                assertTrue(wikipediaField.containsKey("evolution"));
                Map behaviorField = (Map) wikipediaField.get("behavior");
                assertTrue(behaviorField.containsKey("style"));
                assertTrue(behaviorField.containsKey("attack"));
            }

        }


    }

    public void testStringPrefixMatch() throws Exception {
        putDocs(database);
        View view = createView(database);

        view.updateIndex();

        QueryOptions options = new QueryOptions();
        options.setPrefixMatchLevel(1);
        options.setStartKey("f");
        options.setEndKey("f");
        List<QueryRow> rows = view.queryWithOptions(options);

        Assert.assertEquals(2, rows.size());
        Assert.assertEquals("five", rows.get(0).getKey());
        Assert.assertEquals("four", rows.get(1).getKey());
    }

    public void testArrayPrefixMatch() throws Exception {
        putDocs(database);

        View view = database.getView("aview");
        view.setMapReduce(new Mapper() {

            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                Assert.assertNotNull(document.get("_id"));
                Assert.assertNotNull(document.get("_rev"));
                String key = (String)document.get("key");
                if (key != null) {
                    String first = key.substring(0, 1);
                    emitter.emit(Arrays.asList(first, key), null);
                }
            }
        }, null, "1");

        view.updateIndex();

        QueryOptions options = new QueryOptions();
        options.setPrefixMatchLevel(1);
        options.setStartKey(Arrays.asList("f"));
        options.setEndKey(options.getStartKey());
        List<QueryRow> rows = view.queryWithOptions(options);

        Assert.assertEquals(2, rows.size());
        Assert.assertEquals(Arrays.asList("f", "five"), rows.get(0).getKey());
        Assert.assertEquals(Arrays.asList("f", "four"), rows.get(1).getKey());
    }

    public void testAllDocsPrefixMatch() throws CouchbaseLiteException {
        database.getDocument("aaaaaaa").createRevision().save();
        database.getDocument("a11zzzzz").createRevision().save();
        database.getDocument("a七乃又直ந்த").createRevision().save();
        database.getDocument("A1").createRevision().save();
        database.getDocument("bcd").createRevision().save();
        database.getDocument("01234").createRevision().save();

        QueryOptions options = new QueryOptions();
        options.setPrefixMatchLevel(1);
        options.setStartKey("a");
        options.setEndKey("a");

        Map<String, Object> result = database.getAllDocs(options);
        assertNotNull(result);
        List<QueryRow> rows = (List<QueryRow>) result.get("rows");
        assertNotNull(rows);

        // 1 < a < 七 - order is ascending by default
        assertEquals(3, rows.size());
        assertEquals("a11zzzzz", rows.get(0).getKey());
        assertEquals("aaaaaaa", rows.get(1).getKey());
        assertEquals("a七乃又直ந்த", rows.get(2).getKey());
    }

    /**
     * in View_Tests.m
     * - (void) test06_ViewCustomFilter
     *
     * https://github.com/couchbase/couchbase-lite-java-core/issues/303
     */
    public void testViewCustomFilter() throws Exception {
        View view = database.getView("vu");
        view.setMapReduce(new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                emitter.emit(document.get("name"), document.get("skin"));
            }
        }, null, "1");

        Map<String,Object> docProperties1 = new HashMap<String,Object>();
        docProperties1.put("name", "Barry");
        docProperties1.put("skin", "none");
        putDoc(database, docProperties1);
        Map<String,Object> docProperties2 = new HashMap<String,Object>();
        docProperties2.put("name", "Terry");
        docProperties2.put("skin", "furry");
        putDoc(database, docProperties2);
        Map<String,Object> docProperties3 = new HashMap<String,Object>();
        docProperties3.put("name", "Wanda");
        docProperties3.put("skin", "scaly");
        putDoc(database, docProperties3);

        // match all
        Query query = view.createQuery();
        Predicate<QueryRow> postFilterAll = new Predicate<QueryRow>(){
            public boolean apply(QueryRow type){
                return true;
            }
        };
        query.setPostFilter(postFilterAll);
        QueryEnumerator rows = query.run();
        assertEquals(3, rows.getCount());
        for(int i = 0; i < rows.getCount(); i++){
            Log.e(Log.TAG_QUERY, ""+ rows.getRow(i).getKey() + " => " + rows.getRow(i).getValue());
        }
        assertEquals(docProperties1.get("name"), rows.getRow(0).getKey());
        assertEquals(docProperties1.get("skin"), rows.getRow(0).getValue());
        assertEquals(docProperties2.get("name"), rows.getRow(1).getKey());
        assertEquals(docProperties2.get("skin"), rows.getRow(1).getValue());
        assertEquals(docProperties3.get("name"), rows.getRow(2).getKey());
        assertEquals(docProperties3.get("skin"), rows.getRow(2).getValue());


        // match  zero
        Predicate<QueryRow> postFilterNone = new Predicate<QueryRow>(){
            public boolean apply(QueryRow type){
                return false;
            }
        };
        query.setPostFilter(postFilterNone);
        rows = query.run();
        assertEquals(0, rows.getCount());


        // match two
        Predicate<QueryRow> postFilter = new Predicate<QueryRow>(){
            public boolean apply(QueryRow type){
                if(type.getValue() instanceof String){
                    String val = (String)type.getValue();
                    if(val != null && val.endsWith("y")){
                        return true;
                    }
                }
                return false;
            }
        };
        query.setPostFilter(postFilter);
        rows = query.run();
        assertEquals(2, rows.getCount());
        assertEquals(docProperties2.get("name"), rows.getRow(0).getKey());
        assertEquals(docProperties2.get("skin"), rows.getRow(0).getValue());
        assertEquals(docProperties3.get("name"), rows.getRow(1).getKey());
        assertEquals(docProperties3.get("skin"), rows.getRow(1).getValue());
    }
    /**
     * in View_Tests.m
     * - (void) test06_AllDocsCustomFilter
     *
     * https://github.com/couchbase/couchbase-lite-java-core/issues/303
     */
    public void testAllDocsCustomFilter() throws Exception{
        Map<String,Object> docProperties1 = new HashMap<String,Object>();
        docProperties1.put("_id", "1");
        docProperties1.put("name", "Barry");
        docProperties1.put("skin", "none");
        putDoc(database, docProperties1);
        Map<String,Object> docProperties2 = new HashMap<String,Object>();
        docProperties2.put("_id", "2");
        docProperties2.put("name", "Terry");
        docProperties2.put("skin", "furry");
        putDoc(database, docProperties2);
        Map<String,Object> docProperties3 = new HashMap<String,Object>();
        docProperties3.put("_id", "3");
        docProperties3.put("name", "Wanda");
        docProperties3.put("skin", "scaly");
        putDoc(database, docProperties3);
        database.clearDocumentCache();

        Log.d(TAG, "---- QUERYIN' ----");
        Query query = database.createAllDocumentsQuery();
        query.setPostFilter(new Predicate<QueryRow>(){
            public boolean apply(QueryRow type){
                Log.e(TAG, "apply()");
                if(type.getDocument().getProperty("skin") != null && type.getDocument().getProperty("skin") instanceof String) {
                    String skin = (String) type.getDocument().getProperty("skin");
                    if(skin.endsWith("y")){
                        return true;
                    }
                }
                return false;
            }
        });
        QueryEnumerator rows = query.run();
        assertEquals(2, rows.getCount());
        assertEquals(docProperties2.get("_id"), rows.getRow(0).getKey());
        assertEquals(docProperties3.get("_id"), rows.getRow(1).getKey());
    }

    public void testQueryEnumerationImplementsIterable() {
        assertTrue(new QueryEnumerator(null, new ArrayList<QueryRow>(), 0) instanceof Iterable);
    }

    /**
     * int ViewInternal_Tests.m
     * - (void) test_ConflictWinner
     */
    public void testConflictWinner() throws CouchbaseLiteException {
        // If a view is re-indexed, and a document in the view has gone into conflict,
        // rows emitted by the earlier 'losing' revision shouldn't appear in the view.
        List<RevisionInternal> docs = putDocs(database);
        RevisionInternal leaf1 = docs.get(1);

        View view = createView(database);
        view.updateIndex();
        List<Map<String,Object>> dump = view.dump();
        Log.v(TAG, "View dump: " + dump);
        Assert.assertEquals(5, dump.size());
        Assert.assertEquals("\"five\"", dump.get(0).get("key"));
        Assert.assertEquals(5, dump.get(0).get("seq"));
        Assert.assertEquals("\"four\"", dump.get(1).get("key"));
        Assert.assertEquals(2, dump.get(1).get("seq"));
        Assert.assertEquals("\"one\"", dump.get(2).get("key"));
        Assert.assertEquals(3, dump.get(2).get("seq"));
        Assert.assertEquals("\"three\"", dump.get(3).get("key"));
        Assert.assertEquals(4, dump.get(3).get("seq"));
        Assert.assertEquals("\"two\"", dump.get(4).get("key"));
        Assert.assertEquals(1, dump.get(4).get("seq"));

        // Create a conflict, won by the new revision:
        Map<String,Object> props = new HashMap<String,Object>();
        props.put("_id", "44444");
        props.put("_rev", "1-~~~~~");  // higher revID, will win conflict
        props.put("key", "40ur");
        RevisionInternal leaf2 = new RevisionInternal(props);
        database.forceInsert(leaf2, new ArrayList<String>(), null);
        Assert.assertEquals(leaf1.getDocId(),leaf2.getDocId());

        // Update the view -- should contain only the key from the new rev, not the old:
        view.updateIndex();
        dump = view.dump();
        Log.d(TAG, "View dump: " + dump);
        Assert.assertEquals(5, dump.size());
        Assert.assertEquals("\"40ur\"", dump.get(0).get("key"));
        Assert.assertEquals(6, dump.get(0).get("seq"));
        Assert.assertEquals("\"five\"", dump.get(1).get("key"));
        Assert.assertEquals(5, dump.get(1).get("seq"));
        Assert.assertEquals("\"one\"", dump.get(2).get("key"));
        Assert.assertEquals(3, dump.get(2).get("seq"));
        Assert.assertEquals("\"three\"", dump.get(3).get("key"));
        Assert.assertEquals(4, dump.get(3).get("seq"));
        Assert.assertEquals("\"two\"", dump.get(4).get("key"));
        Assert.assertEquals(1, dump.get(4).get("seq"));
    }
    /**
     * int ViewInternal_Tests.m
     * - (void) test_ConflictWinner
     *
     * https://github.com/couchbase/couchbase-lite-android/issues/494
     */
    public void testConflictLoser() throws CouchbaseLiteException {
        // Like the ConflictWinner test, except the newer revision is the loser,
        // so it shouldn't be indexed at all. Instead, the older still-winning revision
        // should be indexed again.
        List<RevisionInternal> docs = putDocs(database);
        RevisionInternal leaf1 = docs.get(1);

        View view = createView(database);
        view.updateIndex();
        List<Map<String,Object>> dump = view.dump();
        Log.d(TAG, "View dump: " + dump);
        Assert.assertEquals(5, dump.size());
        Assert.assertEquals("\"five\"", dump.get(0).get("key"));
        Assert.assertEquals(5, dump.get(0).get("seq"));
        Assert.assertEquals("\"four\"", dump.get(1).get("key"));
        Assert.assertEquals(2, dump.get(1).get("seq"));
        Assert.assertEquals("\"one\"", dump.get(2).get("key"));
        Assert.assertEquals(3, dump.get(2).get("seq"));
        Assert.assertEquals("\"three\"", dump.get(3).get("key"));
        Assert.assertEquals(4, dump.get(3).get("seq"));
        Assert.assertEquals("\"two\"", dump.get(4).get("key"));
        Assert.assertEquals(1, dump.get(4).get("seq"));

        // Create a conflict, won by the new revision:
        Map<String,Object> props = new HashMap<String,Object>();
        props.put("_id", "44444");
        props.put("_rev", "1-...."); // lower revID, will lose conflict
        props.put("key", "40ur");
        RevisionInternal leaf2 = new RevisionInternal(props);
        database.forceInsert(leaf2, new ArrayList<String>(), null);
        Assert.assertEquals(leaf1.getDocId(),leaf2.getDocId());

        // Update the view -- should contain only the key from the new rev, not the old:
        view.updateIndex();
        dump = view.dump();
        Log.d(TAG, "View dump: " + dump);
        Assert.assertEquals(5, dump.size());
        Assert.assertEquals("\"five\"", dump.get(0).get("key"));
        Assert.assertEquals(5, dump.get(0).get("seq"));
        Assert.assertEquals("\"four\"", dump.get(1).get("key"));
        Assert.assertEquals(2, dump.get(1).get("seq"));
        Assert.assertEquals("\"one\"", dump.get(2).get("key"));
        Assert.assertEquals(3, dump.get(2).get("seq"));
        Assert.assertEquals("\"three\"", dump.get(3).get("key"));
        Assert.assertEquals(4, dump.get(3).get("seq"));
        Assert.assertEquals("\"two\"", dump.get(4).get("key"));
        Assert.assertEquals(1, dump.get(4).get("seq"));
    }
    /**
     * https://github.com/couchbase/couchbase-lite-android/issues/494
     */
    public void testIndexingOlderRevision() throws CouchbaseLiteException{
        // In case conflictWinner was deleted, conflict loser should be indexed.

        // create documents
        List<RevisionInternal> docs = putDocs(database);
        RevisionInternal leaf1 = docs.get(1);

        Assert.assertEquals("four", database.getDocument("44444").getProperty("key"));

        // update index
        View view = createView(database);
        view.updateIndex();
        List<Map<String,Object>> dump = view.dump();
        Log.d(TAG, "View dump: " + dump);
        Assert.assertEquals(5, dump.size());
        Assert.assertEquals("\"five\"", dump.get(0).get("key"));
        Assert.assertEquals(5, dump.get(0).get("seq"));
        Assert.assertEquals("\"four\"", dump.get(1).get("key"));
        Assert.assertEquals(2, dump.get(1).get("seq"));
        Assert.assertEquals("\"one\"", dump.get(2).get("key"));
        Assert.assertEquals(3, dump.get(2).get("seq"));
        Assert.assertEquals("\"three\"", dump.get(3).get("key"));
        Assert.assertEquals(4, dump.get(3).get("seq"));
        Assert.assertEquals("\"two\"", dump.get(4).get("key"));
        Assert.assertEquals(1, dump.get(4).get("seq"));

        // Create a conflict, won by the new revision:
        Map<String,Object> props = new HashMap<String,Object>();
        props.put("_id", "44444");
        props.put("_rev", "1-~~~~~");  // higher revID, will win conflict
        props.put("key", "40ur");
        RevisionInternal leaf2 = new RevisionInternal(props);
        database.forceInsert(leaf2, new ArrayList<String>(), null);
        Assert.assertEquals(leaf1.getDocId(),leaf2.getDocId());

        Assert.assertEquals("40ur", database.getDocument("44444").getProperty("key"));

        // Update the view -- should contain only the key from the new rev, not the old:
        view.updateIndex();
        dump = view.dump();
        Log.d(TAG, "View dump: " + dump);
        Assert.assertEquals(5, dump.size());
        Assert.assertEquals("\"40ur\"", dump.get(0).get("key"));
        Assert.assertEquals(6, dump.get(0).get("seq"));
        Assert.assertEquals("\"five\"", dump.get(1).get("key"));
        Assert.assertEquals(5, dump.get(1).get("seq"));
        Assert.assertEquals("\"one\"", dump.get(2).get("key"));
        Assert.assertEquals(3, dump.get(2).get("seq"));
        Assert.assertEquals("\"three\"", dump.get(3).get("key"));
        Assert.assertEquals(4, dump.get(3).get("seq"));
        Assert.assertEquals("\"two\"", dump.get(4).get("key"));
        Assert.assertEquals(1, dump.get(4).get("seq"));


        // create new revision with delete
        RevisionInternal leaf3 = new RevisionInternal("44444", null, true);
        leaf3 = database.putRevision(leaf3, "1-~~~~~", true);
        Assert.assertEquals(leaf1.getDocId(),leaf3.getDocId());
        Assert.assertEquals(true, leaf3.isDeleted());

        Assert.assertEquals("four", database.getDocument("44444").getProperty("key"));

        view.updateIndex();
        dump = view.dump();
        Log.e(TAG, "View dump: " + dump);
        Assert.assertEquals(5, dump.size());
        Assert.assertEquals("\"five\"", dump.get(0).get("key"));
        Assert.assertEquals(5, dump.get(0).get("seq"));
        Assert.assertEquals("\"four\"", dump.get(1).get("key"));
        Assert.assertEquals(2, dump.get(1).get("seq"));
        Assert.assertEquals("\"one\"", dump.get(2).get("key"));
        Assert.assertEquals(3, dump.get(2).get("seq"));
        Assert.assertEquals("\"three\"", dump.get(3).get("key"));
        Assert.assertEquals(4, dump.get(3).get("seq"));
        Assert.assertEquals("\"two\"", dump.get(4).get("key"));
        Assert.assertEquals(1, dump.get(4).get("seq"));
    }

    /**
     * LiveQuery should re-run query from scratch after options are changed
     * https://github.com/couchbase/couchbase-lite-ios/issues/596
     * <p/>
     * in View_Tests.m
     * - (void) test13_LiveQuery_UpdateWhenQueryOptionsChanged
     */
    public void testLiveQueryUpdateWhenQueryOptionsChanged() throws CouchbaseLiteException, InterruptedException {
        View view = database.getView("vu");
        view.setMap(new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                emitter.emit(document.get("sequence"), null);
            }
        }, "1");

        createDocuments(database, 5);

        Query query = view.createQuery();
        QueryEnumerator rows = query.run();

        assertEquals(5, rows.getCount());

        int expectedKey = 0;
        for (QueryRow row : rows) {
            assertEquals(((Integer) row.getKey()).intValue(), expectedKey++);
        }

        LiveQuery liveQuery = view.createQuery().toLiveQuery();
        final CountDownLatch latch1 = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(2);
        liveQuery.addChangeListener(new LiveQuery.ChangeListener() {
            @Override
            public void changed(LiveQuery.ChangeEvent event) {
                Log.e(TAG, "---- changed ---");
                latch1.countDown();
                latch2.countDown();
            }
        });
        liveQuery.start();

        boolean success1 = latch1.await(3, TimeUnit.SECONDS);
        assertTrue(success1);

        rows = liveQuery.getRows();
        assertNotNull(rows);
        assertEquals(5, rows.getCount());
        expectedKey = 0;
        for (QueryRow row : rows) {
            assertEquals(((Integer) row.getKey()).intValue(), expectedKey++);
        }

        liveQuery.setStartKey(2);
        liveQuery.queryOptionsChanged();

        boolean success2 = latch2.await(3, TimeUnit.SECONDS);
        assertTrue(success2);

        rows = liveQuery.getRows();
        assertNotNull(rows);
        assertEquals(3, rows.getCount());
        expectedKey = 2;
        for (QueryRow row : rows) {
            assertEquals(((Integer) row.getKey()).intValue(), expectedKey++);
        }

        liveQuery.stop();
    }

    /**
     * Tests that when converting from a Query to a LiveQuery, all properties are preserved.
     * More speficially tests that the Query copy constructor copies all fields.
     *
     * Regression test for couchbase/couchbase-lite-java-core#585.
     */
    public void testQueryToLiveQuery() throws Exception {
        View view = database.getView("vu");
        Query query = view.createQuery();

        query.setAllDocsMode(Query.AllDocsMode.INCLUDE_DELETED);
        assertTrue(query.shouldIncludeDeleted());
        query.setPrefetch(true);
        query.setPrefixMatchLevel(1);
        query.setStartKey(Arrays.asList("hello", "wo"));
        query.setStartKey(Arrays.asList("hello", "wo", Collections.EMPTY_MAP));
        query.setKeys(Arrays.<Object>asList("1", "5", "aaaaa"));
        query.setGroupLevel(2);
        query.setPrefixMatchLevel(2);
        query.setStartKeyDocId("1");
        query.setEndKeyDocId("123456789");
        query.setDescending(true);
        query.setLimit(10);
        query.setIndexUpdateMode(Query.IndexUpdateMode.NEVER);
        query.setSkip(2);
        query.setPostFilter(new Predicate<QueryRow>() {
            @Override
            public boolean apply(QueryRow type) {
                return true;
            }
        });
        query.setMapOnly(true);

        // Lets also test the copy constructor itself
        // But first ensure our assumptions hold
        if (Modifier.isAbstract(Query.class.getModifiers())) {
            fail("Assumption failed: test needs to be updated");
        }
        if (Query.class.getSuperclass() != Object.class) {
            // sameFields(Object, Object) does not compare fields of superclasses
            fail("Assumption failed: test needs to be updated");
        }
        Constructor<Query> copyConstructor = null;
        try {
            copyConstructor = Query.class.getDeclaredConstructor(Database.class, Query.class);
        } catch (NoSuchMethodException e) {
            fail("Copy constructor not found");
        }
        if (Modifier.isPrivate(copyConstructor.getModifiers())) {
            fail("Copy constructor is private");
        }

        // Constructor is package protected
        copyConstructor.setAccessible(true);

        // Now make some copies
        LiveQuery copy1 = query.toLiveQuery();
        Query copy2 = copyConstructor.newInstance(query.getDatabase(), query);

        sameFields(query, copy1);
        sameFields(query, copy2);
    }

    private static <T> void sameFields(T expected, T actual) throws Exception {
        // Compare the fields in the expected class (Query)
        // and not the ones in actual, as it may be a subclass (LiveQuery)
        assertNotNull(actual);
        Class<?> clazz = expected.getClass();
        Field[] fields = clazz.getDeclaredFields();

        boolean compared = false;
        for (Field field: fields) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            field.setAccessible(true);
            Object expectedObj = field.get(expected);
            Object actualObj = field.get(actual);
            assertEquals(expectedObj, actualObj);
            compared = true;
        }
        assertTrue("No fields to compare?!?", compared);
    }

    public void testDeleteIndexTest() {
        View newView = database.getView("newview");
        newView.deleteIndex();
        newView.setMap(
                new Mapper() {
                    @Override
                    public void map(Map<String, Object> documentProperties, Emitter emitter) {
                        String documentId = (String) documentProperties.get("_id");
                        emitter.emit(documentId, "Document id is " + documentId);
                    }
                },
                "1"
        );
        Query query = database.getView("newview").createQuery();
        QueryEnumerator queryResult = null;
        try {
            queryResult = query.run();
        } catch (CouchbaseLiteException e) {
            Log.e(TAG, "Failed to run Query.run() for indexDeleted", e);
            fail("Failed to run Query.run() for indexDeleted");
        }

        for (Iterator<QueryRow> it = queryResult.iterator(); it.hasNext(); ) {
            QueryRow row = it.next();
            Log.i("deleteIndexTest", (String) row.getValue());
        }
    }
}
