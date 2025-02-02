// This file was autogenerated when creating security.ts using @guardian/cdk-cli
// It is a starting point for migration to CDK *only*. Please check the output carefully before deploying

import 'source-map-support/register';
import { App } from '@aws-cdk/core';
import { SecurityHQ } from '../lib/security-hq';

const app = new App();
new SecurityHQ(app, 'security-hq', { stack: 'security' });
