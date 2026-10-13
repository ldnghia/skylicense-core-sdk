#include <stdio.h>
#include "licensecore_api.h"

int main(void) {
    licensecore_ExportedSymbols *api = licensecore_symbols();
    const char *version = api->kotlin.root.com.dcorp.licensecore.get_LICENSE_CORE_SDK_VERSION();
    printf("licensecore.dll loaded OK. SDK_VERSION = %s\n", version);
    return 0;
}
