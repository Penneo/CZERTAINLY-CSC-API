#!/bin/bash

set -e

# Get the current git commit SHA (short version)
COMMIT_SHA=$(git rev-parse --short HEAD)

# Check if there are uncommitted changes
if ! git diff-index --quiet HEAD --; then
    echo "Warning: You have uncommitted changes. The SHA might not reflect the actual build."
    COMMIT_SHA="${COMMIT_SHA}-dirty"
fi

IMAGE_BASE="harbor.ops.penneo.cloud/identiteam/czertainly-csc-api"
IMAGE_TAG="${IMAGE_BASE}:${COMMIT_SHA}"
IMAGE_LATEST="${IMAGE_BASE}:latest"

echo "Building Docker image with tag: ${COMMIT_SHA}"
echo "Full image name: ${IMAGE_TAG}"

# Build the image
docker build -t "${IMAGE_TAG}" -t "${IMAGE_LATEST}" . --platform=linux/amd64

echo "Pushing image: ${IMAGE_TAG}"
docker push "${IMAGE_TAG}"

echo "Pushing image: ${IMAGE_LATEST}"
docker push "${IMAGE_LATEST}"

echo ""
echo "Build and push complete!"
echo "To use this image in your deployment, update the image to:"
echo "  ${IMAGE_TAG}"
