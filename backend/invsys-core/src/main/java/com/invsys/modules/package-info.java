/**
 * Bounded business feature packages (vertical slices).
 *
 * <p>Each submodule is a Spring Modulith application module. Cross-module
 * collaboration goes through {@code api} / {@code domain} named interfaces or
 * Spring application events — never another module's {@code service} or
 * {@code repository} packages.
 */
package com.invsys.modules;
