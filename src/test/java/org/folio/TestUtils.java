package org.folio;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.commons.io.IOUtils;
import org.folio.rest.util.TestConstants;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.support.AbstractApplicationContext;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Stream;

import io.vertx.core.Context;
import io.vertx.core.Vertx;

public class TestUtils {
  public static final String PURCHASE_METHOD = "df26d81b-9d63-4ff8-bf41-49bf75cfa70e";
  private TestUtils() {}

  public static String getMockData(String path) throws IOException {

    try (InputStream resourceAsStream = TestConstants.class.getClassLoader().getResourceAsStream(path)) {
      if (resourceAsStream != null) {
        return IOUtils.toString(resourceAsStream, StandardCharsets.UTF_8);
      } else {
        StringBuilder sb = new StringBuilder();
        try (Stream<String> lines = Files.lines(Paths.get(path))) {
          lines.forEach(sb::append);
        }
        return sb.toString();
      }
    }
  }

  public static void setInternalState(Object target, String field, Object value) {
    Class<?> c = target.getClass();
    try {
      var f = getDeclaredFieldRecursive(field, c);
      f.setAccessible(true);
      f.set(target, value);
    } catch (Exception e) {
      throw new RuntimeException(
        "Unable to set internal state on a private field. [...]", e);
    }
  }

  private static Field getDeclaredFieldRecursive(String field, Class<?> cls) {
    try {
      return cls.getDeclaredField(field);
    } catch (NoSuchFieldException e) {
      if (cls.getSuperclass() != null) {
        return getDeclaredFieldRecursive(field, cls.getSuperclass());
      }
      throw new RuntimeException(String.format("Unable to find field: %s for class: %s", field, cls.getName()), e);
    }
  }

  public static Context mockContext(Vertx vertx) {
    AbstractApplicationContext springContextMock = mock(AbstractApplicationContext.class);
    when(springContextMock.getAutowireCapableBeanFactory()).thenReturn(mock(AutowireCapableBeanFactory.class));
    Context context = vertx.getOrCreateContext();
    context.put("springContext", springContextMock);
    return context;
  }
}
