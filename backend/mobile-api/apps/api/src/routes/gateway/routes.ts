import type { FastifyInstance, FastifyReply, FastifyRequest } from 'fastify';
// Ambient augmentation from @fastify/multipart: adds isMultipart()/parts() to
// FastifyRequest. Imported for its types even though the plugin is registered
// in server.ts.
import '@fastify/multipart';
import type { Modality } from '@supernutrition/shared';
import {
  FORBIDDEN_CONTEXT_FIELDS,
  PhotoInferenceRequestSchema,
} from '../../modules/inference/presentation/inference.schemas.js';
import {
  GATEWAY_CODES,
  GatewayError,
  type AuthContext,
  type MarketResolution,
} from '../../security/index.js';
import { mapError, rateLimitHeaders, resolveCorrelationId } from '../../middleware/index.js';
import { runInferencePipeline, type PipelineDeps } from './pipeline.js';
import { cancellationStatus } from './transfer-state.js';
import {
  CONTEXT_PART_CONTENT_TYPE,
  MAX_PAYLOAD_BYTES,
  UNDECLARED_PART_CONTENT_TYPE,
  validateGatewayHeaders,
  type InferenceRequestInput,
} from './inference.handler.js';

/**
 * Fastify wiring for `/gw/v1`.
 *
 * This module owns transport concerns only. Every decision lives in
 * `pipeline.ts`, which is why the pipeline is testable without a server.
 *
 * Note what this file does NOT import: no repository, no database package, no
 * module infrastructure. The gateway's inability to durably store user content
 * is a dependency-graph property enforced by lint and by the boundary scan
 * (docs/09 §9.6).
 */

export interface GatewayRouteDeps extends PipelineDeps {
  readonly authenticate: (request: FastifyRequest) => Promise<AuthContext>;
  readonly issueTestSession: () => Promise<{
    accessToken: string;
    refreshToken: string;
    accessExpiresAt: string;
    refreshExpiresAt: string;
    authMode: string;
  }>;
  readonly resolveMarketPolicy: (
    marketId: string,
    modality: Modality,
    now: Date,
  ) => MarketResolution;
  readonly policyVersion: string;
}

const correlationOf = (request: FastifyRequest): string =>
  resolveCorrelationId(
    (request.headers['x-correlation-id'] ?? request.headers['x-request-id']) as string | undefined,
  );

const fail = (reply: FastifyReply, error: unknown, correlationId: string): FastifyReply => {
  const mapped = mapError(error, correlationId);
  return reply.status(mapped.status).headers(mapped.headers).send(mapped.body);
};

export const registerGatewayRoutes = async (
  app: FastifyInstance,
  deps: GatewayRouteDeps,
): Promise<void> => {
  app.post('/gw/v1/inference/photo', async (request, reply) => {
    const correlationId = correlationOf(request);
    let attemptId: string | null = null;

    try {
      if (!request.isMultipart()) {
        throw new GatewayError(GATEWAY_CODES.UNSUPPORTED_MEDIA_TYPE, {
          details: [{ field: 'body', issue: 'expected_multipart' }],
        });
      }

      // M3 — header checks and authentication happen BEFORE the body is read.
      //
      // request.parts() buffers the whole photo, up to 8MB, and it used to run
      // first: an unauthenticated caller could pin 8MB of server memory per
      // request by posting to this route. The release's `pre_authentication`
      // phase is header-only for exactly this reason, and every body-reading
      // step is published in the `post_authentication` phase (D-02, #50).
      const { requestId } = validateGatewayHeaders({
        contractVersion: request.headers['x-contract-version'] as string | undefined,
        requestId: request.headers['x-request-id'] as string | undefined,
      });
      const auth = await deps.authenticate(request);

      let contextRaw: string | null = null;
      let bytes: Uint8Array | null = null;
      let contentType = '';

      // H-05 — @fastify/multipart finishes only once every file stream has been
      // consumed. This loop read a file named `payload` and ignored any other,
      // so one extra file part left a stream open and could hold the request,
      // and the worker with it, until something timed out.
      //
      // Duplicates were quietly worse: the last part won, so which photo got
      // analysed depended on the order the client happened to send them.
      //
      // The first problem is recorded rather than thrown, so the remaining parts
      // are still drained and the request completes before the 4xx.
      let rejection: { field: string; issue: string } | null = null;
      const refuse = (field: string, issue: string) => {
        rejection ??= { field, issue };
      };

      // P-05 — the release declares `parts = ["context", "payload"]` and
      // `context_part_content_type = "application/json"`. The previous loop
      // enforced neither: a `context` part of any type was read, and an
      // unexpected TEXT part was dropped in silence, so a request that did not
      // match the released envelope was served as though it did.
      //
      // A part without a filename always reaches the field branch, whatever it
      // declares, and busboy carries the declared type on `mimetype` — defaulting
      // an undeclared part to `text/plain`. So the check is on the mimetype, and
      // `text/plain` is treated as "did not say" rather than "said the wrong
      // thing": the release constrains what the part is, not that a client must
      // announce it, and refusing silence would break conforming clients.
      for await (const part of request.parts()) {
        if (part.fieldname === 'context') {
          if (contextRaw !== null) {
            if (part.type === 'file') part.file.resume();
            refuse('context', 'duplicate_part');
            continue;
          }
          const declared = (part as { mimetype?: string }).mimetype;
          if (
            declared !== undefined &&
            declared !== UNDECLARED_PART_CONTENT_TYPE &&
            declared !== CONTEXT_PART_CONTENT_TYPE
          ) {
            if (part.type === 'file') part.file.resume();
            refuse('context', 'unsupported_context_content_type');
            continue;
          }
          if (part.type === 'file') {
            contextRaw = (await part.toBuffer()).toString('utf-8');
            continue;
          }
          // @fastify/multipart parses a field that declares `application/json`,
          // so `value` is already an object there and only a string when the
          // part declared nothing. `String()` on the former yields
          // "[object Object]", which then failed JSON.parse and reported an
          // unparseable body for a request that was perfectly well formed.
          contextRaw =
            typeof part.value === 'string' ? part.value : JSON.stringify(part.value ?? null);
          continue;
        }

        if (part.type === 'file') {
          if (part.fieldname !== 'payload') {
            // Discarded without buffering: an unexpected file is refused, and
            // reading it into memory to throw it away is the wrong trade.
            part.file.resume();
            refuse(part.fieldname, 'unexpected_file_part');
            continue;
          }
          if (bytes !== null) {
            part.file.resume();
            refuse('payload', 'duplicate_part');
            continue;
          }
          const buffer = await part.toBuffer();
          bytes = new Uint8Array(buffer);
          contentType = part.mimetype;
          continue;
        }

        // A text part that is neither of the two the release declares.
        refuse(part.fieldname, 'unexpected_part');
      }

      if (rejection !== null) {
        throw new GatewayError(GATEWAY_CODES.VALIDATION_ERROR, { details: [rejection] });
      }

      if (contextRaw === null) {
        throw new GatewayError(GATEWAY_CODES.VALIDATION_ERROR, {
          details: [{ field: 'context', issue: 'missing' }],
        });
      }
      if (bytes === null) {
        throw new GatewayError(GATEWAY_CODES.VALIDATION_ERROR, {
          details: [{ field: 'payload', issue: 'missing-normalized-photo-payload' }],
        });
      }

      let parsedContext: unknown;
      try {
        parsedContext = JSON.parse(contextRaw);
      } catch {
        throw new GatewayError(GATEWAY_CODES.VALIDATION_ERROR, {
          details: [{ field: 'context', issue: 'unparseable_json' }],
        });
      }

      // M6 — "parsed as JSON" is not "is the shape we expect". Valid JSON
      // includes null, numbers, strings and arrays, and every one of those was
      // cast straight to InferenceRequestInput. `null` then turned the first
      // property read into a TypeError and the gateway reported its own crash as
      // a 500 for what was a malformed client request; the scalars survived as
      // far as the version check and came back as `unsupported-version`, which
      // sends a client looking for the wrong problem.
      if (
        parsedContext === null ||
        typeof parsedContext !== 'object' ||
        Array.isArray(parsedContext)
      ) {
        throw new GatewayError(GATEWAY_CODES.VALIDATION_ERROR, {
          details: [{ field: 'context', issue: 'not_a_json_object' }],
        });
      }
      // H-03 — object-ness at the root said nothing about what was inside it.
      // `consentReceipt: null` survived this far and became a TypeError on the
      // first property read, which the gateway reported as internal-error: a
      // 500, and an invitation to retry a request that cannot succeed.
      //
      // The release already describes this shape and a schema for it already
      // exists. Validating here means a malformed request is refused before an
      // effect is claimed, a request id is burned, bytes are stored, or a
      // provider is called.
      //
      // P-02 — the validated value used to be discarded and the raw JSON cast
      // in its place, on the grounds that the schema omitted `modality` and
      // `claimedMarketJurisdiction`. That made this a gate over a subset: the
      // schema also omitted `inferenceContext.configurationVersion` and
      // `priorMealSummary`, and the latter is forwarded to the provider under a
      // released 280-character bound that nothing enforced. The schema now
      // covers the whole envelope and its output is what the pipeline runs on.
      //
      // A forbidden field is refused before the schema, because zod strips
      // unknown keys and cannot tell "ignored" from "rejected" — and the release
      // draws exactly that distinction for `processingRegion`.
      const forbidden = FORBIDDEN_CONTEXT_FIELDS.filter(
        (field) => parsedContext !== null && field in (parsedContext as Record<string, unknown>),
      );
      if (forbidden.length > 0) {
        throw new GatewayError(GATEWAY_CODES.VALIDATION_ERROR, {
          details: forbidden.map((field) => ({
            field: `context.${field}`,
            issue: 'field_not_accepted',
          })),
        });
      }

      const shape = PhotoInferenceRequestSchema.safeParse(parsedContext);
      if (!shape.success) {
        throw new GatewayError(GATEWAY_CODES.VALIDATION_ERROR, {
          details: shape.error.issues.slice(0, 8).map((issue) => ({
            field: ['context', ...issue.path.map(String)].join('.'),
            issue: issue.code,
          })),
        });
      }

      // The parsed value, not the raw JSON — no `as` over unvalidated data, and
      // unknown keys are stripped, which is the release's stated behaviour for
      // everything except the forbidden field refused above.
      //
      // Honest about what this line does and does not do: the enforcement lives
      // in the schema above, and the pipeline additionally hand-picks the fields
      // it forwards, so swapping this back to the raw object fails no test
      // today. It is defence in depth against the next field added downstream
      // without a matching schema entry — the exact way `priorMealSummary`
      // escaped in the first place.
      const context = shape.data as InferenceRequestInput;
      attemptId = typeof context?.attemptId === 'string' ? context.attemptId : null;

      const result = await runInferencePipeline(deps, {
        context,
        payload: { bytes, contentType },
        auth,
        clientIp: request.ip,
        requestId,
      });

      return reply
        .status(result.status)
        .header('X-Correlation-Id', correlationId)
        .headers(result.headers ?? {})
        .send(result.body);
    } catch (error) {
      // Disposal is unconditional. The pipeline disposes in its own `finally`,
      // but a failure before the pipeline is entered must not leave bytes held.
      if (attemptId) deps.payloads.dispose(attemptId);
      return fail(reply, error, correlationId);
    }
  });

  app.delete<{ Params: { attemptId: string } }>(
    '/gw/v1/inference/:attemptId',
    async (request, reply) => {
      const correlationId = correlationOf(request);
      try {
        // M2 — the authenticated identity is USED, not merely obtained. Attempt
        // ids are chosen by the client, so discarding this result let any
        // authenticated installation cancel, and read the transfer record of,
        // any attempt id it cared to guess.
        const auth = await deps.authenticate(request);
        const record = deps.transfers.cancel(request.params.attemptId, auth.installationId);

        // H-01 — disposal happened BEFORE this check, and cancel() returns null
        // for an attempt owned by a different installation. A caller who knew a
        // live attempt id could therefore make its owner lose the buffered
        // photo: their request would fail, with nothing to connect it to an
        // action someone else took. Nothing is disposed until the caller is
        // known to own the attempt.
        if (record === null) {
          // #49 — before answering "unknown", ask whether ANOTHER instance holds
          // this attempt. With the shared registry composed, an attempt started
          // on instance A and cancelled against instance B used to be reported
          // as unknown by B — a photo mid-transfer, described as an attempt that
          // does not exist.
          //
          // The cancellation is recorded in the registry regardless of which
          // instance received it, so the holder sees it and aborts. This
          // instance still cannot produce the record: it does not hold the state
          // or the bytes, and inventing transferState and bytesTransferred would
          // be exactly the fabrication the 204 path was removed for. It fails
          // closed with a typed error that says so.
          if (deps.attempts !== null) {
            const holder = await deps.attempts.holderOf(request.params.attemptId);
            if (holder !== null && holder !== deps.instanceId) {
              await deps.attempts.markCancelled(request.params.attemptId);
              // PROVIDER_UNAVAILABLE is a compromise, and worth naming as one.
              // The published taxonomy is frozen and has no code for "this
              // instance cannot serve your attempt"; inventing one is a contract
              // change. Of the codes that exist, this is the only one whose
              // SHAPE is right — 503, recoverable, retryAfter 60 — and shape is
              // what the client acts on. `internal-error` would be 500 with
              // recoverable:false, telling a user to give up on something that
              // works on the next request. Tracked for the next release.
              throw new GatewayError(GATEWAY_CODES.PROVIDER_UNAVAILABLE, {
                details: [{ field: 'attemptId', issue: 'held_by_another_instance' }],
              });
            }
          }

          // No record the caller may see: never begun, finished longer ago than
          // the retention window, or begun by a different installation. All
          // three answer identically, so the response cannot be used to
          // enumerate attempt ids.
          //
          // This used to answer 204, which by contract means "nothing was sent,
          // this is a complete guarantee" — a promise about the user's photo the
          // gateway had no basis for. Declining to answer is honest and needs no
          // new code in the published taxonomy.
          throw new GatewayError(GATEWAY_CODES.VALIDATION_ERROR, {
            details: [{ field: 'attemptId', issue: 'unknown_attempt' }],
          });
        }

        // #49 — publish the cancellation so the other instances stop too. This
        // instance owns the attempt, but a replica may still be holding a late
        // provider response for it.
        if (deps.attempts !== null) {
          await deps.attempts.markCancelled(request.params.attemptId);
        }

        // Authorised: the caller owns this attempt, so its bytes go now.
        deps.payloads.dispose(request.params.attemptId);

        const status = cancellationStatus(record);
        reply.status(status).header('X-Correlation-Id', correlationId);
        // 204 carries no body by definition; the record is still authoritative
        // and repeat cancellation returns the same terminal state.
        return status === 204 ? reply.send() : reply.send(record);
      } catch (error) {
        return fail(reply, error, correlationId);
      }
    },
  );

  /**
   * Declared by the release and by docs/06 §6.5, but `text` is a RESERVED
   * modality in v1 (schema.toml `[modality] supported = ["photo"]`). It answers
   * `invalid-modality` — "not supported by this release" — rather than 404,
   * which would say the path does not exist and send a client hunting for a
   * typo instead of falling back to manual entry.
   */
  app.post('/gw/v1/inference/text', async (request, reply) =>
    fail(
      reply,
      new GatewayError(GATEWAY_CODES.INVALID_MODALITY, {
        details: [{ field: 'modality', issue: 'reserved_in_this_release' }],
      }),
      correlationOf(request),
    ),
  );

  app.post('/gw/v1/session', async (request, reply) => {
    const correlationId = correlationOf(request);
    try {
      // Unauthenticated by design — it is where a client gets its first
      // credential — and therefore the one endpoint that must be throttled by
      // address, or anyone can mint sessions in a loop for free. RATE_LIMIT_RULES
      // has carried an `auth-endpoint` rule since it was written; nothing used
      // it.
      const decision = await deps.limiter.check({ layer: 'auth-endpoint', principal: request.ip });
      reply.headers(rateLimitHeaders(decision));
      if (!decision.allowed) {
        throw new GatewayError(GATEWAY_CODES.RATE_LIMITED, {
          ...(decision.retryAfterSeconds !== null
            ? { retryAfterOverride: decision.retryAfterSeconds }
            : {}),
        });
      }

      const pair = await deps.issueTestSession();
      return reply.status(201).header('X-Correlation-Id', correlationId).send(pair);
    } catch (error) {
      return fail(reply, error, correlationId);
    }
  });

  app.get<{ Querystring: { jurisdiction?: string; modality?: string } }>(
    '/gw/v1/policy/market',
    async (request, reply) => {
      const correlationId = correlationOf(request);
      try {
        // `modality` is non-optional in market-policy.toml. Defaulting it would
        // answer the photo posture to a client that asked about nothing —
        // exactly the cross-modality inference MP-4 forbids.
        const modality = request.query.modality;
        if (modality === undefined || modality === '') {
          throw new GatewayError(GATEWAY_CODES.VALIDATION_ERROR, {
            details: [{ field: 'modality', issue: 'required' }],
          });
        }
        if (modality !== 'photo' && modality !== 'text') {
          throw new GatewayError(GATEWAY_CODES.INVALID_MODALITY, {
            details: [{ field: 'modality', issue: 'unsupported' }],
          });
        }

        const now = deps.now();
        const resolved = deps.resolveMarketPolicy(request.query.jurisdiction ?? '', modality, now);

        return reply
          .status(200)
          .header('Cache-Control', 'no-store')
          .header('X-Correlation-Id', correlationId)
          .send({
            marketJurisdiction: resolved.marketJurisdiction,
            processingRegion: resolved.processingRegion,
            residencyPosture: resolved.residencyPosture,
            remotePermitted: resolved.remotePermitted,
            consentCopyVersion: resolved.consentCopyVersion,
            acceptedInputLanguages: resolved.acceptedInputLanguages,
            policyVersion: deps.policyVersion,
            resolvedAt: now.toISOString(),
          });
      } catch (error) {
        return fail(reply, error, correlationId);
      }
    },
  );

  app.addContentTypeParser(
    'application/octet-stream',
    { bodyLimit: MAX_PAYLOAD_BYTES },
    (_request, payload, done) => done(null, payload),
  );
};
