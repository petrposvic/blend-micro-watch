#ifndef MyClock_h
#define MyClock_h

#include "Arduino.h"

class MyClock {
  public:
    MyClock();
    void proceed();
    void set(int hours, int minutes, int seconds);
    int h, m, s;
  private:
    int ms;
    long jobTime;
};

#endif

