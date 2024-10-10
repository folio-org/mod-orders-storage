package org.folio.services.setting.util;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum SettingKey {

  CENTRAL_ORDERING_ENABLED("ALLOW_ORDERING_WITH_AFFILIATED_LOCATIONS");

  private final String name;

}
