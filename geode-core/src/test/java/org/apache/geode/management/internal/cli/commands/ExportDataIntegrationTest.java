/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 */

package org.apache.geode.management.internal.cli.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.IntStream;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;

import org.apache.geode.cache.Region;
import org.apache.geode.cache.RegionShortcut;
import org.apache.geode.management.internal.cli.i18n.CliStrings;
import org.apache.geode.management.internal.cli.util.CommandStringBuilder;
import org.apache.geode.test.junit.rules.GfshShellConnectionRule;
import org.apache.geode.test.junit.rules.ServerStarterRule;
import org.apache.geode.test.junit.categories.IntegrationTest;

@Category(IntegrationTest.class)
public class ExportDataIntegrationTest {
  private static final String TEST_REGION_NAME = "testRegion";
  private static final String SNAPSHOT_FILE = "snapshot.gfd";
  private static final String SNAPSHOT_DIR = "snapshots";
  private static final int DATA_POINTS = 10;

  @ClassRule
  public static ServerStarterRule server = new ServerStarterRule().withJMXManager()
      .withRegion(RegionShortcut.PARTITION, TEST_REGION_NAME).withEmbeddedLocator();

  @Rule
  public GfshShellConnectionRule gfsh = new GfshShellConnectionRule();

  @Rule
  public TemporaryFolder tempDir = new TemporaryFolder();

  private Region<String, String> region;
  private Path snapshotFile;
  private Path snapshotDir;

  @Before
  public void setup() throws Exception {
    gfsh.connectAndVerify(server.getEmbeddedLocatorPort(),
        GfshShellConnectionRule.PortType.locator);
    region = server.getCache().getRegion(TEST_REGION_NAME);
    loadRegion("value");
    Path basePath = tempDir.getRoot().toPath();
    snapshotFile = basePath.resolve(SNAPSHOT_FILE);
    snapshotDir = basePath.resolve(SNAPSHOT_DIR);
  }

  @Test
  public void testExport() throws Exception {
    String exportCommand = buildBaseExportCommand()
        .addOption(CliStrings.EXPORT_DATA__FILE, snapshotFile.toString()).getCommandString();
    gfsh.executeAndVerifyCommand(exportCommand);
    assertThat(gfsh.getGfshOutput()).contains("Data successfully exported ");
  }

  @Test
  public void testParallelExport() throws Exception {
    String exportCommand =
        buildBaseExportCommand().addOption(CliStrings.EXPORT_DATA__DIR, snapshotDir.toString())
            .addOption(CliStrings.EXPORT_DATA__PARALLEL, "true").getCommandString();
    gfsh.executeAndVerifyCommand(exportCommand);
    assertThat(gfsh.getGfshOutput()).contains("Data successfully exported ");
  }

  @Test
  public void testInvalidMember() throws Exception {
    String invalidMemberName = "invalidMember";
    String invalidMemberCommand = new CommandStringBuilder(CliStrings.EXPORT_DATA)
        .addOption(CliStrings.MEMBER, invalidMemberName)
        .addOption(CliStrings.EXPORT_DATA__REGION, TEST_REGION_NAME)
        .addOption(CliStrings.EXPORT_DATA__FILE, snapshotFile.toString()).getCommandString();
    gfsh.executeCommand(invalidMemberCommand);
    assertThat(gfsh.getGfshOutput()).contains("Member " + invalidMemberName + " not found");
  }

  @Test
  public void testNonExistentRegion() throws Exception {
    String nonExistentRegionCommand = new CommandStringBuilder(CliStrings.EXPORT_DATA)
        .addOption(CliStrings.MEMBER, server.getName())
        .addOption(CliStrings.EXPORT_DATA__REGION, "/nonExistentRegion")
        .addOption(CliStrings.EXPORT_DATA__FILE, snapshotFile.toString()).getCommandString();
    gfsh.executeCommand(nonExistentRegionCommand);
    assertThat(gfsh.getGfshOutput()).contains("Could not process command due to error. Region");
  }

  @Test
  public void testInvalidFile() throws Exception {
    String invalidFileCommand = buildBaseExportCommand()
        .addOption(CliStrings.EXPORT_DATA__FILE, snapshotFile.toString() + ".invalid")
        .getCommandString();
    gfsh.executeCommand(invalidFileCommand);
    assertThat(gfsh.getGfshOutput())
        .contains("Invalid file type, the file extension must be \".gfd\"");
  }

  @Test
  public void testMissingRegion() throws Exception {
    String missingRegionCommand = new CommandStringBuilder(CliStrings.EXPORT_DATA)
        .addOption(CliStrings.MEMBER, server.getName())
        .addOption(CliStrings.EXPORT_DATA__FILE, snapshotFile.toString()).getCommandString();
    gfsh.executeCommand(missingRegionCommand);
    assertThat(gfsh.getGfshOutput()).contains("You should specify option");
  }

  @Test
  public void testMissingMember() throws Exception {
    String missingMemberCommand = new CommandStringBuilder(CliStrings.EXPORT_DATA)
        .addOption(CliStrings.EXPORT_DATA__REGION, TEST_REGION_NAME)
        .addOption(CliStrings.EXPORT_DATA__FILE, snapshotFile.toString()).getCommandString();
    gfsh.executeCommand(missingMemberCommand);
    assertThat(gfsh.getGfshOutput()).contains("You should specify option");
  }

  @Test
  public void testMissingFileAndDirectory() throws Exception {
    String missingFileAndDirCommand = buildBaseExportCommand().getCommandString();
    gfsh.executeCommand(missingFileAndDirCommand);
    assertThat(gfsh.getGfshOutput()).contains("Must specify a location to save snapshot");
  }

  @Test
  public void testParallelExportWithOnlyFile() throws Exception {
    String exportCommand =
        buildBaseExportCommand().addOption(CliStrings.EXPORT_DATA__FILE, snapshotFile.toString())
            .addOption(CliStrings.EXPORT_DATA__PARALLEL, "true").getCommandString();
    gfsh.executeCommand(exportCommand);
    assertThat(gfsh.getGfshOutput()).contains("Must specify a directory to save snapshot files");
  }

  @Test
  public void testSpecifyingDirectoryAndFileCommands() throws Exception {
    String exportCommand =
        buildBaseExportCommand().addOption(CliStrings.EXPORT_DATA__FILE, snapshotFile.toString())
            .addOption(CliStrings.EXPORT_DATA__DIR, snapshotDir.toString()).getCommandString();
    gfsh.executeCommand(exportCommand);
    assertThat(gfsh.getGfshOutput())
        .contains("Options \"file\" and \"dir\" cannot be specified at the same time");

    assertFalse(Files.exists(snapshotDir));
  }

  private void loadRegion(String value) {
    IntStream.range(0, DATA_POINTS).forEach(i -> region.put("key" + i, value));
  }

  private CommandStringBuilder buildBaseExportCommand() {
    return new CommandStringBuilder(CliStrings.EXPORT_DATA)
        .addOption(CliStrings.MEMBER, server.getName())
        .addOption(CliStrings.EXPORT_DATA__REGION, TEST_REGION_NAME);
  }
}
