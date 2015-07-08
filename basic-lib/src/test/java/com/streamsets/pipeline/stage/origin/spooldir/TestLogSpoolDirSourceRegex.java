/**
 * (c) 2014 StreamSets, Inc. All rights reserved. May not
 * be copied, modified, or distributed in whole or part without
 * written consent of StreamSets, Inc.
 */
package com.streamsets.pipeline.stage.origin.spooldir;

import com.streamsets.pipeline.api.BatchMaker;
import com.streamsets.pipeline.api.Record;
import com.streamsets.pipeline.api.StageException;
import com.streamsets.pipeline.config.DataFormat;
import com.streamsets.pipeline.config.FileCompression;
import com.streamsets.pipeline.config.LogMode;
import com.streamsets.pipeline.config.OnParseError;
import com.streamsets.pipeline.config.PostProcessingOptions;
import com.streamsets.pipeline.lib.parser.log.RegExConfig;
import com.streamsets.pipeline.sdk.SourceRunner;
import com.streamsets.pipeline.sdk.StageRunner;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TestLogSpoolDirSourceRegex {

  private static final String CUSTOM_LOG_FORMAT = "%h %l %u [%t] \"%m %U %H\" %>s %b";
  private static final String REGEX =
    "^(\\S+) (\\S+) (\\S+) \\[([\\w:/]+\\s[+\\-]\\d{4})\\] \"(\\S+ \\S+ \\S+)\" (\\d{3}) (\\d+)";
  private static final String INVALID_REGEX =
    "^(\\S+) (\\S+) (\\S+) \\[([\\w:/]+\\s[+\\-]\\d{4})\\] \"(\\S+ \\S+ \\S+\" (\\d{3}) (\\d+)";
  private static final List<RegExConfig> REGEX_CONFIG = new ArrayList<>();

  static {
    RegExConfig r1 = new RegExConfig();
    r1.fieldPath = "remoteHost";
    r1.group = 1;
    REGEX_CONFIG.add(r1);
    RegExConfig r2 = new RegExConfig();
    r2.fieldPath = "logName";
    r2.group = 2;
    REGEX_CONFIG.add(r2);
    RegExConfig r3 = new RegExConfig();
    r3.fieldPath = "remoteUser";
    r3.group = 3;
    REGEX_CONFIG.add(r3);
    RegExConfig r4 = new RegExConfig();
    r4.fieldPath = "requestTime";
    r4.group = 4;
    REGEX_CONFIG.add(r4);
    RegExConfig r5 = new RegExConfig();
    r5.fieldPath = "request";
    r5.group = 5;
    REGEX_CONFIG.add(r5);
    RegExConfig r6 = new RegExConfig();
    r6.fieldPath = "status";
    r6.group = 6;
    REGEX_CONFIG.add(r6);
    RegExConfig r7 = new RegExConfig();
    r7.fieldPath = "bytesSent";
    r7.group = 7;
    REGEX_CONFIG.add(r7);
  }

  private String createTestDir() {
    File f = new File("target", UUID.randomUUID().toString());
    Assert.assertTrue(f.mkdirs());
    return f.getAbsolutePath();
  }

  private final static String LINE1 = "127.0.0.1 ss h [10/Oct/2000:13:55:36 -0700] \"GET /apache_pb.gif HTTP/1.0\" 200 2326";
  private final static String LINE2 = "127.0.0.2 ss m [10/Oct/2000:13:55:36 -0800] \"GET /apache_pb.gif HTTP/2.0\" 200 2326";

  private File createLogFile() throws Exception {
    File f = new File(createTestDir(), "test.log");
    Writer writer = new FileWriter(f);
    IOUtils.write(LINE1 + "\n", writer);
    IOUtils.write(LINE2, writer);
    writer.close();
    return f;
  }

  private SpoolDirSource createSource() {
    return new SpoolDirSource(DataFormat.LOG, "UTF-8", false, 100, createTestDir(), 10, 1, "file-[0-9].log", 10, null,
                              FileCompression.NONE, null,
      PostProcessingOptions.ARCHIVE, createTestDir(), 10, null, null, -1, '^', '^', '^', null, 0, 0,
      null, 0, LogMode.REGEX, 1000, true, CUSTOM_LOG_FORMAT, REGEX, REGEX_CONFIG, null, null, false, null,
      OnParseError.ERROR, 0, null);
  }

  @Test
  public void testProduceFullFile() throws Exception {
    SpoolDirSource source = createSource();
    SourceRunner runner = new SourceRunner.Builder(SpoolDirDSource.class, source).addOutputLane("lane").build();
    runner.runInit();
    try {
      BatchMaker batchMaker = SourceRunner.createTestBatchMaker("lane");
      Assert.assertEquals("-1", source.produce(createLogFile(), "0", 10, batchMaker));
      StageRunner.Output output = SourceRunner.getOutput(batchMaker);
      List<Record> records = output.getRecords().get("lane");
      Assert.assertNotNull(records);
      Assert.assertEquals(2, records.size());

      Assert.assertFalse(records.get(0).has("/truncated"));

      Record record = records.get(0);

      Assert.assertEquals(LINE1, record.get().getValueAsMap().get("originalLine").getValueAsString());

      Assert.assertFalse(record.has("/truncated"));

      Assert.assertTrue(record.has("/remoteHost"));
      Assert.assertEquals("127.0.0.1", record.get("/remoteHost").getValueAsString());

      Assert.assertTrue(record.has("/logName"));
      Assert.assertEquals("ss", record.get("/logName").getValueAsString());

      Assert.assertTrue(record.has("/remoteUser"));
      Assert.assertEquals("h", record.get("/remoteUser").getValueAsString());

      Assert.assertTrue(record.has("/requestTime"));
      Assert.assertEquals("10/Oct/2000:13:55:36 -0700", record.get("/requestTime").getValueAsString());

      Assert.assertTrue(record.has("/request"));
      Assert.assertEquals("GET /apache_pb.gif HTTP/1.0", record.get("/request").getValueAsString());

      Assert.assertTrue(record.has("/status"));
      Assert.assertEquals("200", record.get("/status").getValueAsString());

      Assert.assertTrue(record.has("/bytesSent"));
      Assert.assertEquals("2326", record.get("/bytesSent").getValueAsString());

      record = records.get(1);

      Assert.assertEquals(LINE2, records.get(1).get().getValueAsMap().get("originalLine").getValueAsString());
      Assert.assertFalse(record.has("/truncated"));

      Assert.assertTrue(record.has("/remoteHost"));
      Assert.assertEquals("127.0.0.2", record.get("/remoteHost").getValueAsString());

      Assert.assertTrue(record.has("/logName"));
      Assert.assertEquals("ss", record.get("/logName").getValueAsString());

      Assert.assertTrue(record.has("/remoteUser"));
      Assert.assertEquals("m", record.get("/remoteUser").getValueAsString());

      Assert.assertTrue(record.has("/requestTime"));
      Assert.assertEquals("10/Oct/2000:13:55:36 -0800", record.get("/requestTime").getValueAsString());

      Assert.assertTrue(record.has("/request"));
      Assert.assertEquals("GET /apache_pb.gif HTTP/2.0", record.get("/request").getValueAsString());

      Assert.assertTrue(record.has("/status"));
      Assert.assertEquals("200", record.get("/status").getValueAsString());

      Assert.assertTrue(record.has("/bytesSent"));
      Assert.assertEquals("2326", record.get("/bytesSent").getValueAsString());

    } finally {
      runner.runDestroy();
    }
  }

  @Test
  public void testProduceLessThanFile() throws Exception {
    SpoolDirSource source = createSource();
    SourceRunner runner = new SourceRunner.Builder(SpoolDirDSource.class, source).addOutputLane("lane").build();
    runner.runInit();
    try {
      BatchMaker batchMaker = SourceRunner.createTestBatchMaker("lane");
      String offset = source.produce(createLogFile(), "0", 1, batchMaker);
      //FIXME
      Assert.assertEquals("83", offset);
      StageRunner.Output output = SourceRunner.getOutput(batchMaker);
      List<Record> records = output.getRecords().get("lane");
      Assert.assertNotNull(records);
      Assert.assertEquals(1, records.size());


      Record record = records.get(0);
      Assert.assertFalse(record.has("/truncated"));

      Assert.assertTrue(record.has("/remoteHost"));
      Assert.assertEquals("127.0.0.1", record.get("/remoteHost").getValueAsString());

      Assert.assertTrue(record.has("/logName"));
      Assert.assertEquals("ss", record.get("/logName").getValueAsString());

      Assert.assertTrue(record.has("/remoteUser"));
      Assert.assertEquals("h", record.get("/remoteUser").getValueAsString());

      Assert.assertTrue(record.has("/requestTime"));
      Assert.assertEquals("10/Oct/2000:13:55:36 -0700", record.get("/requestTime").getValueAsString());

      Assert.assertTrue(record.has("/request"));
      Assert.assertEquals("GET /apache_pb.gif HTTP/1.0", record.get("/request").getValueAsString());

      Assert.assertTrue(record.has("/status"));
      Assert.assertEquals("200", record.get("/status").getValueAsString());

      Assert.assertTrue(record.has("/bytesSent"));
      Assert.assertEquals("2326", record.get("/bytesSent").getValueAsString());

      batchMaker = SourceRunner.createTestBatchMaker("lane");
      offset = source.produce(createLogFile(), offset, 1, batchMaker);
      Assert.assertEquals("165", offset);
      output = SourceRunner.getOutput(batchMaker);
      records = output.getRecords().get("lane");
      Assert.assertNotNull(records);
      Assert.assertEquals(1, records.size());

      Assert.assertEquals(LINE2, records.get(0).get().getValueAsMap().get("originalLine").getValueAsString());
      Assert.assertFalse(records.get(0).has("/truncated"));

      record = records.get(0);
      Assert.assertTrue(record.has("/remoteHost"));
      Assert.assertEquals("127.0.0.2", record.get("/remoteHost").getValueAsString());

      Assert.assertTrue(record.has("/logName"));
      Assert.assertEquals("ss", record.get("/logName").getValueAsString());

      Assert.assertTrue(record.has("/remoteUser"));
      Assert.assertEquals("m", record.get("/remoteUser").getValueAsString());

      Assert.assertTrue(record.has("/requestTime"));
      Assert.assertEquals("10/Oct/2000:13:55:36 -0800", record.get("/requestTime").getValueAsString());

      Assert.assertTrue(record.has("/request"));
      Assert.assertEquals("GET /apache_pb.gif HTTP/2.0", record.get("/request").getValueAsString());

      Assert.assertTrue(record.has("/status"));
      Assert.assertEquals("200", record.get("/status").getValueAsString());

      Assert.assertTrue(record.has("/bytesSent"));
      Assert.assertEquals("2326", record.get("/bytesSent").getValueAsString());


      batchMaker = SourceRunner.createTestBatchMaker("lane");
      offset = source.produce(createLogFile(), offset, 1, batchMaker);
      Assert.assertEquals("-1", offset);
      output = SourceRunner.getOutput(batchMaker);
      records = output.getRecords().get("lane");
      Assert.assertNotNull(records);
      Assert.assertEquals(0, records.size());

    } finally {
      runner.runDestroy();
    }
  }

  @Test(expected = StageException.class)
  public void testInvalidRegEx() throws StageException {
    SpoolDirSource spoolDirSource = new SpoolDirSource(DataFormat.LOG, "UTF-8", false, 100, createTestDir(), 10, 1,
      "file-[0-9].log", 10, null, FileCompression.NONE, null,
      PostProcessingOptions.ARCHIVE, createTestDir(), 10, null, null, -1, '^', '^', '^', null, 0, 0,
      null, 0, LogMode.REGEX, 1000, true, CUSTOM_LOG_FORMAT, INVALID_REGEX, REGEX_CONFIG, null, null, false, null,
      OnParseError.ERROR, 0, null);
    SourceRunner runner = new SourceRunner.Builder(SpoolDirDSource.class, spoolDirSource).addOutputLane("lane").build();
    runner.runInit();
  }

  @Test(expected = StageException.class)
  public void testInvalidRegGroupNumber() throws StageException {

    List<RegExConfig> regExConfig = new ArrayList<>();
    regExConfig.addAll(REGEX_CONFIG);
    RegExConfig r8 = new RegExConfig();
    r8.fieldPath = "nonExistingGroup";
    r8.group = 8;
    regExConfig.add(r8);

    SpoolDirSource spoolDirSource = new SpoolDirSource(DataFormat.LOG, "UTF-8", false, 100, createTestDir(), 10, 1,
      "file-[0-9].log", 10, null, FileCompression.NONE, null,
      PostProcessingOptions.ARCHIVE, createTestDir(), 10, null, null, -1, '^', '^', '^',null, 0, 0,
      null, 0, LogMode.REGEX, 1000, true, CUSTOM_LOG_FORMAT, REGEX, regExConfig, null, null, false, null,
      OnParseError.ERROR, 0, null);
    SourceRunner runner = new SourceRunner.Builder(SpoolDirDSource.class, spoolDirSource).addOutputLane("lane").build();
    runner.runInit();
  }

}
