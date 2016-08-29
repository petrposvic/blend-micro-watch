#include "MyClock.h"

MyClock::MyClock() {
}

void MyClock::proceed() {
  double now = millis();
  double diff = now - jobTime;
  if (diff < 1000) {
    return;
  }

  ms += diff;

  while (ms >= 1000) {
    ms -= 1000;
    s++;
    if (s < 60) {
      continue;
    }

    s -= 60;
    m++;
    if (m < 60) {
      continue;
    }

    m -= 60;
    h++;
    if (h < 24) {
      continue;
    }

    h -= 24;
  }

  jobTime = millis();
}

void MyClock::set(int hours, int minutes, int seconds) {
  h = hours;
  m = minutes;
  s = seconds;
}

