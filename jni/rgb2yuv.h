//
// Created by Shrek on 2018/7/21.
//

#ifndef LIPCMD_V0_1_RGB2YUV_H
#define LIPCMD_V0_1_RGB2YUV_H

#include <stdint.h>


void ConvertARGB8888ToYUV420SP(const uint32_t* const input,
                               uint8_t* const output, int width, int height);

void ConvertRGB565ToYUV420SP(const uint16_t* const input, uint8_t* const output,
                             const int width, const int height);


#endif //LIPCMD_V0_1_RGB2YUV_H
