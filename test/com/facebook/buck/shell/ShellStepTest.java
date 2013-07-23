/*
 * Copyright 2012-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.shell;

import static org.easymock.EasyMock.expect;
import static org.junit.Assert.assertEquals;

import com.facebook.buck.step.ExecutionContext;
import com.facebook.buck.testutil.TestConsole;
import com.facebook.buck.util.ProcessExecutor;
import com.facebook.buck.util.Verbosity;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.easymock.EasyMockSupport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;

public class ShellStepTest extends EasyMockSupport {

  private ExecutionContext context;
  private TestConsole console;

  private static final ImmutableList<String> ARGS = ImmutableList.of("bash", "-c", "echo $V1 $V2");

  private static final ImmutableMap<String, String> ENV = ImmutableMap.of(
      "V1", "two words",
      "V2", "$foo'bar'"
  );

  private static final File PATH = new File("/tmp/a b");
  private static final String ERROR_MSG = "some syntax error\ncompilation failed\n";
  private static final String OUTPUT_MSG = "processing data...\n";
  private static final int EXIT_FAILURE = 1;
  private static final int EXIT_SUCCESS = 0;

  @Before
  public void setUp() {
    context = createMock(ExecutionContext.class);
    replayAll();
  }

  @After
  public void tearDown() {
    verifyAll();
  }

  private void prepareContextForOutput(Verbosity verbosity) {
    resetAll();

    console = new TestConsole();
    console.setVerbosity(verbosity);
    ProcessExecutor processExecutor = new ProcessExecutor(console);

    expect(context.getStdErr()).andReturn(console.getStdErr()).anyTimes();
    expect(context.getVerbosity()).andReturn(verbosity).anyTimes();
    expect(context.getProcessExecutor()).andReturn(processExecutor).anyTimes();
    replayAll();
  }

  private static Process createProcess(
      final int exitValue,
      final String stdout,
      final String stderr) {
    return new Process() {

      @Override
      public OutputStream getOutputStream() {
        return null;
      }

      @Override
      public InputStream getInputStream() {
        return new ByteArrayInputStream(stdout.getBytes(Charsets.US_ASCII));
      }

      @Override
      public InputStream getErrorStream() {
        return new ByteArrayInputStream(stderr.getBytes(Charsets.US_ASCII));
      }

      @Override
      public int waitFor() {
        return exitValue;
      }

      @Override
      public int exitValue() {
        return exitValue;
      }

      @Override
      public void destroy() {
      }

    };
  }

  private static ShellStep createCommand(
      ImmutableMap<String, String> env,
      ImmutableList<String> cmd,
      File workingDirectory) {
    return createCommand(
        env,
        cmd,
        workingDirectory,
        /* shouldPrintStdErr */ false,
        /* shouldRecordStdOut */ false);
  }

  private static ShellStep createCommand(boolean shouldPrintStdErr, boolean shouldRecordStdOut) {
    return createCommand(ENV, ARGS, null, shouldPrintStdErr, shouldRecordStdOut);
  }

  private static ShellStep createCommand(
      final ImmutableMap<String, String> env,
      final ImmutableList<String> cmd,
      File workingDirectory,
      final boolean shouldPrintStdErr,
      final boolean shouldRecordStdOut) {
    return new ShellStep(workingDirectory) {
      @Override
      public ImmutableMap<String, String> getEnvironmentVariables(ExecutionContext context) {
        return env;
      }
      @Override
      public String getShortName() {
         return cmd.get(0);
      }
      @Override
      protected ImmutableList<String> getShellCommandInternal(
          ExecutionContext context) {
        return cmd;
      }
      @Override
      protected boolean shouldPrintStdErr(ExecutionContext context) {
        return shouldPrintStdErr;
      }
      @Override
      protected boolean shouldRecordStdout() {
        return shouldRecordStdOut;
      }
    };
  }

  @Test
  public void testDescriptionWithEnvironment() {
    ShellStep command = createCommand(ENV, ARGS, null);
    assertEquals("V1='two words' V2='$foo'\\''bar'\\''' bash -c 'echo $V1 $V2'",
        command.getDescription(context));
  }

  @Test
  public void testDescriptionWithEnvironmentAndPath() {
    ShellStep command = createCommand(ENV, ARGS, PATH);
    assertEquals("(cd '/tmp/a b' && V1='two words' V2='$foo'\\''bar'\\''' bash -c 'echo $V1 $V2')",
        command.getDescription(context));
  }

  @Test
  public void testDescriptionWithPath() {
    ShellStep command = createCommand(ImmutableMap.<String,String>of(), ARGS, PATH);
    assertEquals("(cd '/tmp/a b' && bash -c 'echo $V1 $V2')",
        command.getDescription(context));
  }

  @Test
  public void testDescription() {
    ShellStep command = createCommand(ImmutableMap.<String,String>of(), ARGS, null);
    assertEquals("bash -c 'echo $V1 $V2'", command.getDescription(context));
  }

  @Test
  public void testStdErrPrintedOnErrorIfNotSilentEvenIfNotShouldPrintStdErr() throws Exception {
    ShellStep command = createCommand(/*shouldPrintStdErr*/ false, /*shouldRecordStdout*/ false);
    Process process = createProcess(EXIT_FAILURE, OUTPUT_MSG, ERROR_MSG);
    prepareContextForOutput(Verbosity.STANDARD_INFORMATION);
    command.interactWithProcess(context, process);
    assertEquals(ERROR_MSG, console.getTextWrittenToStdErr());
  }

  @Test
  public void testStdErrNotPrintedOnErrorIfSilentAndNotShouldPrintStdErr() throws Exception {
    ShellStep command = createCommand(/*shouldPrintStdErr*/ false, /*shouldRecordStdout*/ false);
    Process process = createProcess(EXIT_FAILURE, OUTPUT_MSG, ERROR_MSG);
    prepareContextForOutput(Verbosity.SILENT);
    command.interactWithProcess(context, process);
    assertEquals("", console.getTextWrittenToStdErr());
  }

  @Test
  public void testStdErrPrintedOnErrorIfShouldPrintStdErrEvenIfSilent() throws Exception {
    ShellStep command = createCommand(/*shouldPrintStdErr*/ true, /*shouldRecordStdout*/ false);
    Process process = createProcess(EXIT_FAILURE, OUTPUT_MSG, ERROR_MSG);
    prepareContextForOutput(Verbosity.SILENT);
    command.interactWithProcess(context, process);
    assertEquals(ERROR_MSG, console.getTextWrittenToStdErr());
  }

  @Test
  public void testStdErrNotPrintedOnSuccessIfNotShouldPrintStdErr() throws Exception {
    ShellStep command = createCommand(/*shouldPrintStdErr*/ false, /*shouldRecordStdout*/ false);
    Process process = createProcess(EXIT_SUCCESS, OUTPUT_MSG, ERROR_MSG);
    prepareContextForOutput(Verbosity.STANDARD_INFORMATION);
    command.interactWithProcess(context, process);
    assertEquals("", console.getTextWrittenToStdErr());
  }

  @Test
  public void testStdErrPrintedOnSuccessIfShouldPrintStdErrEvenIfSilent() throws Exception {
    ShellStep command = createCommand(/*shouldPrintStdErr*/ true, /*shouldRecordStdout*/ false);
    Process process = createProcess(EXIT_SUCCESS, OUTPUT_MSG, ERROR_MSG);
    prepareContextForOutput(Verbosity.SILENT);
    command.interactWithProcess(context, process);
    assertEquals(ERROR_MSG, console.getTextWrittenToStdErr());
  }

  @Test
  public void testOuputRecordedButNotPrintedIfShouldRecordStdoutEvenIfVerbose() throws Exception {
    ShellStep command = createCommand(/*shouldPrintStdErr*/ false, /*shouldRecordStdout*/ true);
    Process process = createProcess(EXIT_SUCCESS, OUTPUT_MSG, ERROR_MSG);
    prepareContextForOutput(Verbosity.ALL);
    command.interactWithProcess(context, process);
    assertEquals(OUTPUT_MSG, command.getStdOut());
  }

  @Test
  public void testStdOutNotPrintedIfNotShouldRecordStdoutEvenIfVerbose() throws Exception {
    ShellStep command = createCommand(/*shouldPrintStdErr*/ false, /*shouldRecordStdout*/ false);
    Process process = createProcess(EXIT_SUCCESS, OUTPUT_MSG, ERROR_MSG);
    prepareContextForOutput(Verbosity.ALL);
    command.interactWithProcess(context, process);
    assertEquals("", console.getTextWrittenToStdErr());
  }

}
