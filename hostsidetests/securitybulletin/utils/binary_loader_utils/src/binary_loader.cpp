/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "binary_loader.h"

// If dlopen is unnecessary (eg. for shared libs), absoluteBinPath should be empty.
BinaryLoader::BinaryLoader(const std::string absoluteBinPath) {
    if (!absoluteBinPath.empty()) {
        binPath = absoluteBinPath.c_str();
        binHandle = dlopen(binPath, RTLD_NOW);
        if (!binHandle) {
            printf("Error opening binary: %s. Error: %s\n", binPath, dlerror());
        }
    }
}

BinaryLoader::~BinaryLoader() {
    if (binHandle) {
        dlclose(binHandle);
        binHandle = nullptr;
    }
    binPath = nullptr;
    baseAddress = 0;
}

// When functionOffset is 0, return the base address
uintptr_t BinaryLoader::getFunctionAddress(uintptr_t functionOffset) {
    if (!baseAddress) {
        if (!dl_iterate_phdr(callback, (void*)this) || !baseAddress) {
            return 0;
        }
    }
    return baseAddress + functionOffset;
}

// Callback function to iterate loaded binaries
int BinaryLoader::callback(struct dl_phdr_info* info, size_t /* size */, void* data) {
    BinaryLoader* binLoader = (BinaryLoader*)data;

    // Check if the library name matches the binPath and p_type is PT_LOAD
    if (strcmp(info->dlpi_name, binLoader->binPath) == 0) {
        for (size_t j = 0; j < info->dlpi_phnum; ++j) {
            if (info->dlpi_phdr[j].p_type == PT_LOAD) {
                binLoader->baseAddress = (uintptr_t)(info->dlpi_addr + info->dlpi_phdr[j].p_vaddr);
                return 1;
            }
        }
    }
    return 0;
}
