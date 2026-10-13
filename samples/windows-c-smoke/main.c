#include <stdio.h>
#include "licensecore.h"

int main(void) {
    const char *config =
        "{\"baseUrl\":\"https://license.invalid\",\"orgId\":1,\"appSalt\":\"smoke-test-salt\"}";

    lc_handle h = lc_create(config);
    if (h == NULL) {
        char *err = lc_last_error();
        printf("lc_create FAILED: %s\n", err);
        lc_free_string(err);
        return 1;
    }
    printf("lc_create OK, abi_version=%d\n", lc_abi_version());

    char *device_id = lc_device_id(h);
    printf("lc_device_id: %s\n", device_id);
    lc_free_string(device_id);

    char *state = lc_get_state(h);
    printf("lc_get_state: %s\n", state);
    lc_free_string(state);

    lc_destroy(h);
    printf("lc_destroy OK\n");
    return 0;
}
